/**
 * AirTortilla Operations Portal — Lógica de Aplicación Front-End
 * Integración directa con los endpoints existentes del backend:
 *   - Carga en Lote: POST /files/upload-csv (multipart/form-data, param: "file")
 *   - Consulta en Lote: GET /files/zip (flujo binario application/zip)
 *
 * Consultor Web Directo:
 *   - Descarga y procesa el ZIP binario real devuelto por GET /files/zip
 *   - Lee los archivos de partición contenidos en el archivo mediante JSZip
 *   - Muestra las reservas organizadas en tablas de datos separadas por nombre de archivo
 *   - Permite filtrar por archivo de destino y descargar opcionalmente el ZIP
 *
 * Restricción estricta: Sin datos simulados ni respuestas simuladas.
 */

(function () {
  'use strict';

  // Endpoints definidos en CsvController de Spring Boot (/files)
  const API_ENDPOINTS = {
    UPLOAD: '/files/upload-csv',
    ZIP: '/files/zip',
  };

  // Gestión de estado de la interfaz
  let selectedFile = null;
  let isUploading = false;
  let isConsulting = false;
  let lastDownloadedBlob = null;
  let lastArchiveFilename = 'AirTortilla.zip';
  let parsedBatchFiles = []; // Almacena { filename, headers, rows } reales del backend

  // Elementos DOM - Característica 1: Carga en Lote
  const dropZone = document.getElementById('drop-zone');
  const fileInput = document.getElementById('file-input');
  const fileSummary = document.getElementById('file-summary');
  const selectedFileNameEl = document.getElementById('selected-file-name');
  const selectedFileSizeEl = document.getElementById('selected-file-size');
  const btnClearFile = document.getElementById('btn-clear-file');
  const btnUpload = document.getElementById('btn-upload');
  const uploadSpinner = document.getElementById('upload-spinner');
  const uploadBtnText = document.getElementById('upload-btn-text');
  const uploadFeedback = document.getElementById('upload-feedback');
  const uploadFeedbackIcon = document.getElementById('upload-feedback-icon');
  const uploadFeedbackTitle = document.getElementById('upload-feedback-title');
  const uploadFeedbackMsg = document.getElementById('upload-feedback-msg');

  // Elementos DOM - Característica 2: Consultor en Lote
  const btnConsult = document.getElementById('btn-consult');
  const consultSpinner = document.getElementById('consult-spinner');
  const consultBtnText = document.getElementById('consult-btn-text');
  const btnDownloadZip = document.getElementById('btn-download-zip');
  const consultFeedback = document.getElementById('consult-feedback');
  const consultFeedbackIcon = document.getElementById('consult-feedback-icon');
  const consultFeedbackTitle = document.getElementById('consult-feedback-title');
  const consultFeedbackMsg = document.getElementById('consult-feedback-msg');

  // Elementos DOM - Visor de Consultas
  const viewerBadge = document.getElementById('viewer-badge');
  const viewerControls = document.getElementById('viewer-controls');
  const fileFilterSelect = document.getElementById('file-filter-select');
  const viewerEmptyState = document.getElementById('viewer-empty-state');
  const tablesContainer = document.getElementById('tables-container');

  // Utilidad: Formateo de bytes
  function formatBytes(bytes) {
    if (!bytes || bytes === 0) return '0 B';
    const k = 1024;
    const sizes = ['B', 'KB', 'MB', 'GB'];
    const i = Math.floor(Math.log(bytes) / Math.log(k));
    return parseFloat((bytes / Math.pow(k, i)).toFixed(2)) + ' ' + sizes[i];
  }

  // Utilidad: Iconos SVG de estado
  const ICONS = {
    SUCCESS: `<svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="M22 11.08V12a10 10 0 1 1-5.93-9.14"/><polyline points="22 4 12 14.01 9 11.01"/></svg>`,
    ERROR: `<svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><circle cx="12" cy="12" r="10"/><line x1="15" y1="9" x2="9" y2="15"/><line x1="9" y1="9" x2="15" y2="15"/></svg>`,
    INFO: `<svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><circle cx="12" cy="12" r="10"/><line x1="12" y1="16" x2="12" y2="12"/><line x1="12" y1="8" x2="12.01" y2="8"/></svg>`,
  };

  function showFeedback(boxEl, iconEl, titleEl, msgEl, type, title, message) {
    boxEl.className = 'feedback-banner ' + type;
    iconEl.innerHTML = ICONS[type.toUpperCase()] || ICONS.INFO;
    titleEl.textContent = title;
    msgEl.textContent = message;
    boxEl.style.display = 'flex';
  }

  function hideFeedback(boxEl) {
    boxEl.style.display = 'none';
  }

  // Reloj UTC en tiempo real
  function updateTimestamp() {
    const el = document.getElementById('session-timestamp');
    if (el) {
      const now = new Date();
      el.textContent = now.toISOString().replace('T', ' ').substring(0, 19) + ' UTC';
    }
  }
  setInterval(updateTimestamp, 1000);
  updateTimestamp();

  // =========================================================================
  // CARACTERÍSTICA UNO: CARGA DE CSV EN LOTE
  // =========================================================================

  function handleFileSelection(file) {
    if (!file) return;

    const isCsvName = file.name.toLowerCase().endsWith('.csv');
    const isCsvMime = file.type === 'text/csv' || file.type === 'application/vnd.ms-excel' || file.type === '';

    if (!isCsvName && !isCsvMime) {
      showFeedback(
        uploadFeedback,
        uploadFeedbackIcon,
        uploadFeedbackTitle,
        uploadFeedbackMsg,
        'error',
        'Formato de Archivo Inválido',
        'El archivo "' + file.name + '" no es un archivo CSV válido. Por favor seleccione un archivo con extensión .csv.'
      );
      clearFileSelection();
      return;
    }

    selectedFile = file;
    selectedFileNameEl.textContent = file.name;
    selectedFileSizeEl.textContent = formatBytes(file.size);
    fileSummary.style.display = 'flex';
    hideFeedback(uploadFeedback);

    // Validación estricta de la primera línea (cabecera requerida por el backend)
    const REQUIRED_HEADER = 'destino,origin,flightNumber,nombre,edad,documento,asiento,email';
    const reader = new FileReader();
    reader.onload = function (evt) {
      const text = evt.target.result;
      const firstLine = (text.split(/\r?\n/)[0] || '').trim();
      
      if (firstLine.toLowerCase() !== REQUIRED_HEADER.toLowerCase()) {
        btnUpload.disabled = true;
        showFeedback(
          uploadFeedback,
          uploadFeedbackIcon,
          uploadFeedbackTitle,
          uploadFeedbackMsg,
          'error',
          'Cabecera CSV Inválida (Rechazado)',
          'El archivo seleccionado no cumple con el orden requerido por el servidor.\nCabecera encontrada: ' + (firstLine || '(vacía)') + '\nCabecera obligatoria: ' + REQUIRED_HEADER
        );
      } else {
        btnUpload.disabled = false;
        showFeedback(
          uploadFeedback,
          uploadFeedbackIcon,
          uploadFeedbackTitle,
          uploadFeedbackMsg,
          'success',
          'Formato CSV Válido',
          'La estructura de 8 columnas coincide con el esquema requerido: ' + REQUIRED_HEADER
        );
      }
    };
    reader.onerror = function () {
      btnUpload.disabled = true;
      showFeedback(
        uploadFeedback,
        uploadFeedbackIcon,
        uploadFeedbackTitle,
        uploadFeedbackMsg,
        'error',
        'Error de Lectura',
        'No se pudo leer el archivo seleccionado para validar su cabecera.'
      );
    };
    reader.readAsText(file.slice(0, 1024));
  }

  function clearFileSelection() {
    selectedFile = null;
    fileInput.value = '';
    fileSummary.style.display = 'none';
    btnUpload.disabled = true;
  }

  dropZone.addEventListener('click', () => {
    if (!isUploading) fileInput.click();
  });

  dropZone.addEventListener('keydown', (e) => {
    if ((e.key === 'Enter' || e.key === ' ') && !isUploading) {
      e.preventDefault();
      fileInput.click();
    }
  });

  fileInput.addEventListener('change', (e) => {
    if (e.target.files && e.target.files.length > 0) {
      handleFileSelection(e.target.files[0]);
    }
  });

  btnClearFile.addEventListener('click', (e) => {
    e.stopPropagation();
    if (!isUploading) clearFileSelection();
  });

  ['dragenter', 'dragover'].forEach((eventName) => {
    dropZone.addEventListener(eventName, (e) => {
      e.preventDefault();
      e.stopPropagation();
      if (!isUploading) dropZone.classList.add('drag-active');
    });
  });

  ['dragleave', 'drop'].forEach((eventName) => {
    dropZone.addEventListener(eventName, (e) => {
      e.preventDefault();
      e.stopPropagation();
      dropZone.classList.remove('drag-active');
    });
  });

  dropZone.addEventListener('drop', (e) => {
    if (isUploading) return;
    const dt = e.dataTransfer;
    if (dt && dt.files && dt.files.length > 0) {
      handleFileSelection(dt.files[0]);
    }
  });

  // Ejecución de la subida
  btnUpload.addEventListener('click', async () => {
    if (!selectedFile || isUploading) return;

    isUploading = true;
    btnUpload.disabled = true;
    btnUpload.classList.add('loading');
    uploadBtnText.textContent = 'Subiendo archivo en lote...';
    hideFeedback(uploadFeedback);

    const formData = new FormData();
    formData.append('file', selectedFile);

    try {
      const response = await fetch(API_ENDPOINTS.UPLOAD, {
        method: 'POST',
        body: formData,
      });

      const responseText = await response.text();

      if (response.ok) {
        showFeedback(
          uploadFeedback,
          uploadFeedbackIcon,
          uploadFeedbackTitle,
          uploadFeedbackMsg,
          'success',
          'Carga Completada con Éxito [HTTP ' + response.status + ']',
          responseText || 'El archivo en lote se ha cargado correctamente en el servidor.'
        );
      } else {
        showFeedback(
          uploadFeedback,
          uploadFeedbackIcon,
          uploadFeedbackTitle,
          uploadFeedbackMsg,
          'error',
          'Error en la Carga [HTTP ' + response.status + ' ' + (response.statusText || '') + ']',
          responseText || 'El servidor rechazó la solicitud de carga.'
        );
      }
    } catch (err) {
      showFeedback(
        uploadFeedback,
        uploadFeedbackIcon,
        uploadFeedbackTitle,
        uploadFeedbackMsg,
        'error',
        'Error de Red / Conexión',
        'No se pudo conectar con el endpoint (' + API_ENDPOINTS.UPLOAD + '). ' + (err.message || 'Verifique la conexión con el servidor.')
      );
    } finally {
      isUploading = false;
      btnUpload.classList.remove('loading');
      uploadBtnText.textContent = 'Subir Archivo CSV en Lote';
      btnUpload.disabled = !selectedFile;
    }
  });

  // =========================================================================
  // CARACTERÍSTICA DOS: CONSULTOR EN LOTE (TABLAS SEPARADAS POR ARCHIVO Y ZIP)
  // =========================================================================

  function parseContentDispositionFilename(dispositionHeader) {
    if (!dispositionHeader) return null;
    const matches = /filename[^;=\n]*=((['"]).*?\2|[^;\n]*)/.exec(dispositionHeader);
    if (matches != null && matches[1]) {
      return matches[1].replace(/['"]/g, '').trim();
    }
    return null;
  }

  // Parsea el texto CSV en cabeceras y filas
  function parseCsvContent(text) {
    if (!text || !text.trim()) return { headers: [], rows: [] };
    const lines = text.trim().split(/\r?\n/).filter(line => line.trim().length > 0);
    if (lines.length === 0) return { headers: [], rows: [] };

    const headers = lines[0].split(',').map(h => h.trim());
    const rows = [];

    for (let i = 1; i < lines.length; i++) {
      const values = lines[i].split(',').map(v => v.trim());
      if (values.length > 0 && values.some(v => v.length > 0)) {
        rows.push(values);
      }
    }

    return { headers, rows };
  }

  // Traducción de cabeceras de columnas para visualización clara
  const HEADER_TRANSLATIONS = {
    'identifier': 'IDENTIFICADOR',
    'destino': 'DESTINO',
    'origin': 'ORIGEN',
    'flightnumber': 'Nº VUELO',
    'nombre': 'PASAJERO',
    'email': 'CORREO ELECTRÓNICO',
    'edad': 'EDAD',
    'documento': 'DOCUMENTO / DNI',
    'asiento': 'ASIENTO',
  };

  function getDisplayHeader(rawHeader) {
    const key = (rawHeader || '').toLowerCase().trim();
    return HEADER_TRANSLATIONS[key] || rawHeader.toUpperCase();
  }

  // Renderiza las tablas separadas por nombre de archivo
  function renderBatchTables(filesToRender) {
    tablesContainer.innerHTML = '';

    if (!filesToRender || filesToRender.length === 0) {
      viewerEmptyState.style.display = 'flex';
      tablesContainer.style.display = 'none';
      viewerEmptyState.querySelector('.empty-text').textContent = 'No hay archivos que coincidan';
      viewerEmptyState.querySelector('.empty-subtext').textContent = 'No hay registros para la selección actual del filtro.';
      return;
    }

    viewerEmptyState.style.display = 'none';
    tablesContainer.style.display = 'flex';

    filesToRender.forEach((fileObj) => {
      const block = document.createElement('div');
      block.className = 'file-partition-block';

      // Extrae el código de destino si coincide con el formato AirTortilla_XX_YYYY_MM_DD
      let countryCode = '';
      const match = fileObj.filename.match(/AirTortilla_([A-Za-z0-9]+)_/);
      if (match && match[1]) {
        countryCode = match[1];
      }

      // Encabezado de la partición
      const headerDiv = document.createElement('div');
      headerDiv.className = 'file-partition-header';

      headerDiv.innerHTML = `
        <div class="partition-name-group">
          <div class="partition-icon">
            <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
              <path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"/>
              <polyline points="14 2 14 8 20 8"/>
            </svg>
          </div>
          <span class="partition-filename">${escapeHtml(fileObj.filename)}</span>
          ${countryCode ? `<span class="partition-country-tag">DESTINO: ${escapeHtml(countryCode)}</span>` : ''}
        </div>
        <div class="partition-stats">
          <span class="partition-row-count">${fileObj.rows.length} reserva${fileObj.rows.length === 1 ? '' : 's'}</span>
        </div>
      `;

      // Envoltura responsiva de la tabla
      const tableWrapper = document.createElement('div');
      tableWrapper.className = 'table-responsive';

      const table = document.createElement('table');
      table.className = 'data-table';

      // Cabecera de la tabla
      const thead = document.createElement('thead');
      const headerRow = document.createElement('tr');
      fileObj.headers.forEach((colName) => {
        const th = document.createElement('th');
        th.textContent = getDisplayHeader(colName);
        headerRow.appendChild(th);
      });
      thead.appendChild(headerRow);
      table.appendChild(thead);

      // Cuerpo de la tabla
      const tbody = document.createElement('tbody');
      fileObj.rows.forEach((rowValues) => {
        const tr = document.createElement('tr');
        rowValues.forEach((val, colIndex) => {
          const td = document.createElement('td');
          const colHeader = (fileObj.headers[colIndex] || '').toLowerCase();

          if (colHeader === 'identifier') {
            td.className = 'cell-id';
          } else if (colHeader === 'destino' || colHeader === 'origin') {
            td.className = 'cell-badge-code';
          } else if (colHeader === 'flightnumber') {
            td.className = 'cell-mono';
          } else if (colHeader === 'nombre') {
            td.className = 'cell-highlight';
          } else if (colHeader === 'documento' || colHeader === 'asiento') {
            td.className = 'cell-mono';
          }

          td.textContent = val;
          tr.appendChild(td);
        });
        tbody.appendChild(tr);
      });
      table.appendChild(tbody);

      tableWrapper.appendChild(table);
      block.appendChild(headerDiv);
      block.appendChild(tableWrapper);
      tablesContainer.appendChild(block);
    });
  }

  function escapeHtml(str) {
    if (!str) return '';
    return str.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;');
  }

  // Cambio en el desplegable de filtro
  fileFilterSelect.addEventListener('change', () => {
    const selectedVal = fileFilterSelect.value;
    if (selectedVal === 'ALL') {
      renderBatchTables(parsedBatchFiles);
    } else {
      const filtered = parsedBatchFiles.filter(f => f.filename === selectedVal);
      renderBatchTables(filtered);
    }
  });

  // Consultar Resultados en Lote
  btnConsult.addEventListener('click', async () => {
    if (isConsulting) return;

    isConsulting = true;
    btnConsult.disabled = true;
    btnConsult.classList.add('loading');
    consultBtnText.textContent = 'Consultando resultados en lote...';
    hideFeedback(consultFeedback);

    try {
      // Llamada directa al endpoint /files/zip existente
      const response = await fetch(API_ENDPOINTS.ZIP, {
        method: 'GET',
      });

      if (!response.ok) {
        let errorDetails = '';
        try {
          errorDetails = await response.text();
        } catch (_) {
          errorDetails = response.statusText;
        }

        showFeedback(
          consultFeedback,
          consultFeedbackIcon,
          consultFeedbackTitle,
          consultFeedbackMsg,
          'error',
          'Error en la Consulta [HTTP ' + response.status + ' ' + (response.statusText || '') + ']',
          errorDetails || 'No se pudo obtener el archivo de reservas del backend.'
        );
        return;
      }

      // Lectura de la respuesta como flujo binario (Blob, no JSON)
      const blob = await response.blob();
      lastDownloadedBlob = blob;

      const dispositionHeader = response.headers.get('Content-Disposition');
      lastArchiveFilename = parseContentDispositionFilename(dispositionHeader) || 'AirTortilla.zip';

      btnDownloadZip.disabled = false;

      if (typeof JSZip === 'undefined') {
        throw new Error('El motor de procesamiento ZIP (JSZip) no está disponible.');
      }

      const zip = await JSZip.loadAsync(blob);
      const fileNames = Object.keys(zip.files).filter(name => !zip.files[name].dir);

      parsedBatchFiles = [];
      let totalReservationsCount = 0;

      for (const name of fileNames) {
        const fileData = zip.files[name];
        const contentText = await fileData.async('string');
        const parsed = parseCsvContent(contentText);

        parsedBatchFiles.push({
          filename: name,
          headers: parsed.headers,
          rows: parsed.rows,
        });

        totalReservationsCount += parsed.rows.length;
      }

      parsedBatchFiles.sort((a, b) => a.filename.localeCompare(b.filename));

      // Llenar el desplegable de filtros
      fileFilterSelect.innerHTML = '<option value="ALL">Todos los Archivos Particionados (' + parsedBatchFiles.length + ')</option>';
      parsedBatchFiles.forEach((f) => {
        const opt = document.createElement('option');
        opt.value = f.filename;
        opt.textContent = f.filename + ' (' + f.rows.length + ' reservas)';
        fileFilterSelect.appendChild(opt);
      });

      // Actualizar la insignia con un punto separador unicode real (•) en vez de entidades literales
      const pluralArchivos = parsedBatchFiles.length === 1 ? 'ARCHIVO' : 'ARCHIVOS';
      const pluralReservas = totalReservationsCount === 1 ? 'RESERVA' : 'RESERVAS';
      viewerBadge.textContent = `${parsedBatchFiles.length} ${pluralArchivos} • ${totalReservationsCount} ${pluralReservas}`;
      viewerBadge.className = 'viewer-badge active';
      viewerControls.style.display = parsedBatchFiles.length > 0 ? 'flex' : 'none';

      // Renderizar las tablas separadas por archivo
      renderBatchTables(parsedBatchFiles);

      // Notificación de éxito
      showFeedback(
        consultFeedback,
        consultFeedbackIcon,
        consultFeedbackTitle,
        consultFeedbackMsg,
        'success',
        'Consulta Realizada con Éxito',
        'Se obtuvieron ' + parsedBatchFiles.length + ' archivo' + (parsedBatchFiles.length === 1 ? '' : 's') + ' particionado' + (parsedBatchFiles.length === 1 ? '' : 's') + ' con un total de ' + totalReservationsCount + ' registros de reservas.'
      );

    } catch (err) {
      showFeedback(
        consultFeedback,
        consultFeedbackIcon,
        consultFeedbackTitle,
        consultFeedbackMsg,
        'error',
        'Error en la Consulta',
        'No se pudo completar la consulta en lote: ' + (err.message || 'Error desconocido')
      );
    } finally {
      isConsulting = false;
      btnConsult.classList.remove('loading');
      consultBtnText.textContent = 'Consultar Resultados en Lote';
      btnConsult.disabled = false;
    }
  });

  // Botón opcional de descarga ZIP
  btnDownloadZip.addEventListener('click', () => {
    if (!lastDownloadedBlob) return;

    const downloadUrl = window.URL.createObjectURL(lastDownloadedBlob);
    const downloadLink = document.createElement('a');
    downloadLink.style.display = 'none';
    downloadLink.href = downloadUrl;
    downloadLink.download = lastArchiveFilename;
    document.body.appendChild(downloadLink);
    downloadLink.click();
    document.body.removeChild(downloadLink);
    window.URL.revokeObjectURL(downloadUrl);
  });

})();
