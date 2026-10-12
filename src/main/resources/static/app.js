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
    LINE: '/files/line',
    ZIP: '/files/zip',
    CONSULT_FILES: '/files/consult/files',
    CONSULT_INITIALIZE: '/files/consult/line/initialize',
    CONSULT_LINE: '/files/consult/line',
  };

  // Gestión de estado de la interfaz
  let selectedFile = null;
  let isUploading = false;
  let isConsulting = false;
  let lastDownloadedBlob = null;
  let lastArchiveFilename = 'AirTortilla.zip';
  let parsedBatchFiles = []; // Almacena { filename, headers, rows } reales del backend

  // Modalidad de subida: 'batch' | 'line'
  let currentUploadMode = 'batch';
  let parsedCsvLines = []; // Array de strings con cada fila de datos del CSV
  let currentLineIndex = 0; // Índice de la línea actual a enviar
  let isLineUploading = false;

  // Modalidad de consulta: 'batch' | 'line'
  let currentConsultMode = 'batch';
  let isConsultLineLoading = false;
  let isConsultInitialized = false;
  let consultFileCounts = {}; // Mapeo de nombre de archivo -> cantidad de reservas

  // Elementos DOM - Característica 1: Carga
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

  // Elementos DOM - Modalidad y Línea a Línea (Subida)
  const pillModeBatch = document.getElementById('pill-mode-batch');
  const pillModeLine = document.getElementById('pill-mode-line');
  const actionBatchBlock = document.getElementById('action-batch-block');
  const lineStepperBox = document.getElementById('line-stepper-box');
  const lineCounter = document.getElementById('line-counter');
  const lineStatusPill = document.getElementById('line-status-pill');
  const currentLineRaw = document.getElementById('current-line-raw');
  const btnSendLine = document.getElementById('btn-send-line');
  const btnResetLineStepper = document.getElementById('btn-reset-line-stepper');
  const stepSpinner = document.getElementById('step-spinner');
  const stepBtnText = document.getElementById('step-btn-text');
  const lineLogList = document.getElementById('line-log-list');
  const logCount = document.getElementById('log-count');
  const specEndpointLabel = document.getElementById('spec-endpoint-label');
  const specEndpointVal = document.getElementById('spec-endpoint-val');

  // Campos de preview de la línea actual (Subida)
  const fieldDestino = document.getElementById('field-destino');
  const fieldOrigin = document.getElementById('field-origin');
  const fieldFlight = document.getElementById('field-flight');
  const fieldNombre = document.getElementById('field-nombre');
  const fieldEdad = document.getElementById('field-edad');
  const fieldDocumento = document.getElementById('field-documento');
  const fieldAsiento = document.getElementById('field-asiento');
  const fieldEmail = document.getElementById('field-email');

  // Elementos DOM - Característica 2: Modalidad de Consulta
  const pillConsultBatch = document.getElementById('pill-consult-batch');
  const pillConsultLine = document.getElementById('pill-consult-line');
  const consultBatchContent = document.getElementById('consult-batch-content');
  const consultLineContent = document.getElementById('consult-line-content');
  const consultSpecsBox = document.getElementById('consult-specs-box');
  const consultEndpointLabel = document.getElementById('consult-endpoint-label');
  const consultEndpointVal = document.getElementById('consult-endpoint-val');

  // Elementos DOM - Consulta en Lote
  const btnConsult = document.getElementById('btn-consult');
  const consultSpinner = document.getElementById('consult-spinner');
  const consultBtnText = document.getElementById('consult-btn-text');
  const btnDownloadZip = document.getElementById('btn-download-zip');
  const consultFeedback = document.getElementById('consult-feedback');
  const consultFeedbackIcon = document.getElementById('consult-feedback-icon');
  const consultFeedbackTitle = document.getElementById('consult-feedback-title');
  const consultFeedbackMsg = document.getElementById('consult-feedback-msg');

  // Elementos DOM - Consulta Línea a Línea
  const consultFileSelect = document.getElementById('consult-file-select');
  const btnRefreshConsultFiles = document.getElementById('btn-refresh-consult-files');
  const consultIndexCounter = document.getElementById('consult-index-counter');
  const consultStatusPill = document.getElementById('consult-status-pill');
  const consultLineRaw = document.getElementById('consult-line-raw');
  const btnConsultPrev = document.getElementById('btn-consult-prev');
  const btnConsultCurrent = document.getElementById('btn-consult-current');
  const btnConsultCurrentText = document.getElementById('btn-consult-current-text');
  const consultLineSpinner = document.getElementById('consult-line-spinner');
  const btnConsultNext = document.getElementById('btn-consult-next');
  const btnResetConsultIndex = document.getElementById('btn-reset-consult-index');
  const btnReinitConsult = document.getElementById('btn-reinit-consult');

  // Campos de preview de consulta línea a línea
  const cfieldId = document.getElementById('cfield-id');
  const cfieldDestino = document.getElementById('cfield-destino');
  const cfieldOrigin = document.getElementById('cfield-origin');
  const cfieldFlight = document.getElementById('cfield-flight');
  const cfieldNombre = document.getElementById('cfield-nombre');
  const cfieldEmail = document.getElementById('cfield-email');
  const cfieldEdad = document.getElementById('cfield-edad');
  const cfieldDocSeat = document.getElementById('cfield-doc-seat');

  // Elementos DOM - Visor de Consultas (Tablas generales)
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
  // GESTIÓN DE MODALIDADES DE SUBIDA (LOTE vs LÍNEA A LÍNEA)
  // =========================================================================

  function setUploadMode(mode) {
    currentUploadMode = mode;
    if (mode === 'batch') {
      pillModeBatch.classList.add('active');
      pillModeBatch.setAttribute('aria-checked', 'true');
      pillModeLine.classList.remove('active');
      pillModeLine.setAttribute('aria-checked', 'false');

      actionBatchBlock.style.display = 'block';
      lineStepperBox.style.display = 'none';

      specEndpointLabel.textContent = 'ENDPOINT DESTINO (LOTE)';
      specEndpointVal.innerHTML = '<span class="http-badge post">POST</span> /files/upload-csv &bull; multipart/form-data ("file")';
    } else {
      pillModeLine.classList.add('active');
      pillModeLine.setAttribute('aria-checked', 'true');
      pillModeBatch.classList.remove('active');
      pillModeBatch.setAttribute('aria-checked', 'false');

      actionBatchBlock.style.display = 'none';
      lineStepperBox.style.display = 'flex';

      specEndpointLabel.textContent = 'ENDPOINT DESTINO (LÍNEA A LÍNEA)';
      specEndpointVal.innerHTML = '<span class="http-badge post">POST</span> /files/line &bull; text/csv (Cuerpo de una sola línea)';
      updateLineStepperUI();
    }
  }

  pillModeBatch.addEventListener('click', () => setUploadMode('batch'));
  pillModeLine.addEventListener('click', () => setUploadMode('line'));

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

    // Leer el archivo completo para validar la cabecera y extraer las líneas
    const REQUIRED_HEADER = 'destino,origin,flightNumber,nombre,edad,documento,asiento,email';
    const reader = new FileReader();
    reader.onload = function (evt) {
      const fullText = evt.target.result;
      const rawLines = fullText.split(/\r?\n/).map(l => l.trim()).filter(l => l.length > 0);

      if (rawLines.length === 0) {
        btnUpload.disabled = true;
        btnSendLine.disabled = true;
        showFeedback(
          uploadFeedback,
          uploadFeedbackIcon,
          uploadFeedbackTitle,
          uploadFeedbackMsg,
          'error',
          'Archivo Vacío',
          'El archivo CSV seleccionado no contiene líneas de datos.'
        );
        return;
      }

      const firstLine = rawLines[0];
      if (firstLine.toLowerCase() !== REQUIRED_HEADER.toLowerCase()) {
        btnUpload.disabled = true;
        btnSendLine.disabled = true;
        parsedCsvLines = [];
        currentLineIndex = 0;
        updateLineStepperUI();
        showFeedback(
          uploadFeedback,
          uploadFeedbackIcon,
          uploadFeedbackTitle,
          uploadFeedbackMsg,
          'error',
          'Cabecera CSV Inválida (Rechazado)',
          'El archivo seleccionado no cumple con el orden requerido por el servidor.\nCabecera encontrada: ' + firstLine + '\nCabecera obligatoria: ' + REQUIRED_HEADER
        );
      } else {
        // Cabecera válida: extraer las filas de datos (omitiendo la cabecera)
        parsedCsvLines = rawLines.slice(1);
        currentLineIndex = 0;
        btnUpload.disabled = false;
        btnResetLineStepper.disabled = false;
        resetLineLog();
        updateLineStepperUI();

        showFeedback(
          uploadFeedback,
          uploadFeedbackIcon,
          uploadFeedbackTitle,
          uploadFeedbackMsg,
          'success',
          'Formato CSV Válido (' + parsedCsvLines.length + ' líneas de datos)',
          'Estructura de 8 columnas válida. Puede procesar todo en lote o enviar línea a línea.'
        );
      }
    };

    reader.onerror = function () {
      btnUpload.disabled = true;
      btnSendLine.disabled = true;
      showFeedback(
        uploadFeedback,
        uploadFeedbackIcon,
        uploadFeedbackTitle,
        uploadFeedbackMsg,
        'error',
        'Error de Lectura',
        'No se pudo leer el archivo seleccionado para validar su contenido.'
      );
    };

    reader.readAsText(file);
  }

  function clearFileSelection() {
    selectedFile = null;
    fileInput.value = '';
    fileSummary.style.display = 'none';
    btnUpload.disabled = true;
    parsedCsvLines = [];
    currentLineIndex = 0;
    resetLineLog();
    updateLineStepperUI();
  }

  // =========================================================================
  // LÓGICA DE PROCESAMIENTO LÍNEA A LÍNEA (POST /files/line)
  // =========================================================================

  function updateLineStepperUI() {
    const total = parsedCsvLines.length;

    if (total === 0) {
      lineCounter.textContent = 'Línea 0 de 0';
      lineStatusPill.textContent = 'SELECCIONE UN ARCHIVO';
      lineStatusPill.className = 'stepper-status-pill';
      currentLineRaw.textContent = '—';
      fieldDestino.textContent = '—';
      fieldOrigin.textContent = '—';
      fieldFlight.textContent = '—';
      fieldNombre.textContent = '—';
      fieldEdad.textContent = '—';
      fieldDocumento.textContent = '—';
      fieldAsiento.textContent = '—';
      fieldEmail.textContent = '—';
      btnSendLine.disabled = true;
      btnResetLineStepper.disabled = true;
      return;
    }

    if (currentLineIndex < total) {
      const lineNum = currentLineIndex + 1;
      lineCounter.textContent = 'Línea ' + lineNum + ' de ' + total;
      lineStatusPill.textContent = 'LISTO PARA ENVIAR';
      lineStatusPill.className = 'stepper-status-pill';

      const currentCsvString = parsedCsvLines[currentLineIndex];
      currentLineRaw.textContent = currentCsvString;

      const cols = currentCsvString.split(',').map(c => c.trim());
      fieldDestino.textContent = cols[0] || '—';
      fieldOrigin.textContent = cols[1] || '—';
      fieldFlight.textContent = cols[2] || '—';
      fieldNombre.textContent = cols[3] || '—';
      fieldEdad.textContent = cols[4] || '—';
      fieldDocumento.textContent = cols[5] || '—';
      fieldAsiento.textContent = cols[6] || '—';
      fieldEmail.textContent = cols[7] || '—';

      btnSendLine.disabled = isLineUploading;
      stepBtnText.textContent = 'Enviar Línea (' + lineNum + '/' + total + ') y Siguiente';
      btnResetLineStepper.disabled = false;
    } else {
      // Todas las líneas han sido enviadas
      lineCounter.textContent = 'Completado: ' + total + ' de ' + total;
      lineStatusPill.textContent = '✓ TODAS PROCESADAS';
      lineStatusPill.className = 'stepper-status-pill completed';

      currentLineRaw.textContent = '— Todas las líneas han sido enviadas exitosamente al servidor —';
      fieldDestino.textContent = '✓';
      fieldOrigin.textContent = '✓';
      fieldFlight.textContent = '✓';
      fieldNombre.textContent = '✓';
      fieldEdad.textContent = '✓';
      fieldDocumento.textContent = '✓';
      fieldAsiento.textContent = '✓';
      fieldEmail.textContent = '✓';

      btnSendLine.disabled = true;
      stepBtnText.textContent = 'Todas las Líneas Enviadas';
      btnResetLineStepper.disabled = false;
    }
  }

  function resetLineLog() {
    lineLogList.innerHTML = '<li class="log-empty">No se ha enviado ninguna línea todavía.</li>';
    logCount.textContent = '0 procesadas';
  }

  function addLineToLog(lineIndex, csvText, statusText) {
    const emptyLi = lineLogList.querySelector('.log-empty');
    if (emptyLi) emptyLi.remove();

    const li = document.createElement('li');
    li.className = 'line-log-item';
    li.innerHTML = `
      <span class="line-log-check">
        <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="3" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
          <polyline points="20 6 9 17 4 12"/>
        </svg>
      </span>
      <span class="line-log-text">Línea ${lineIndex + 1}: ${escapeHtml(csvText)}</span>
      <span class="line-log-status">${escapeHtml(statusText)}</span>
    `;

    lineLogList.prepend(li);
    logCount.textContent = (lineIndex + 1) + ' procesadas';
  }

  btnResetLineStepper.addEventListener('click', () => {
    currentLineIndex = 0;
    resetLineLog();
    updateLineStepperUI();
    hideFeedback(uploadFeedback);
  });

  // Envío de la línea actual: POST /files/line con text/csv
  btnSendLine.addEventListener('click', async () => {
    if (isLineUploading || currentLineIndex >= parsedCsvLines.length) return;

    const lineToSend = parsedCsvLines[currentLineIndex];
    const lineIndex = currentLineIndex;

    isLineUploading = true;
    btnSendLine.disabled = true;
    btnSendLine.classList.add('loading');
    stepBtnText.textContent = 'Enviando línea ' + (lineIndex + 1) + '...';
    lineStatusPill.textContent = 'ENVIANDO...';
    lineStatusPill.className = 'stepper-status-pill uploading';

    try {
      const response = await fetch(API_ENDPOINTS.LINE, {
        method: 'POST',
        headers: {
          'Content-Type': 'text/csv',
        },
        body: lineToSend,
      });

      if (response.ok) {
        // Éxito: agregamos el checkmark en el log y avanzamos al siguiente
        addLineToLog(lineIndex, lineToSend, 'OK (HTTP ' + response.status + ')');
        currentLineIndex++;
        updateLineStepperUI();

        showFeedback(
          uploadFeedback,
          uploadFeedbackIcon,
          uploadFeedbackTitle,
          uploadFeedbackMsg,
          'success',
          'Línea ' + (lineIndex + 1) + ' Procesada con Éxito [HTTP ' + response.status + ']',
          'Se procesó y particionó: ' + lineToSend
        );
      } else {
        const errorText = await response.text();
        lineStatusPill.textContent = 'ERROR EN LÍNEA';
        lineStatusPill.className = 'stepper-status-pill';
        showFeedback(
          uploadFeedback,
          uploadFeedbackIcon,
          uploadFeedbackTitle,
          uploadFeedbackMsg,
          'error',
          'Error en Línea ' + (lineIndex + 1) + ' [HTTP ' + response.status + ']',
          errorText || 'El backend rechazó el procesamiento de esta línea.'
        );
      }
    } catch (err) {
      lineStatusPill.textContent = 'ERROR DE RED';
      lineStatusPill.className = 'stepper-status-pill';
      showFeedback(
        uploadFeedback,
        uploadFeedbackIcon,
        uploadFeedbackTitle,
        uploadFeedbackMsg,
        'error',
        'Error de Red al Enviar Línea',
        'No se pudo conectar con ' + API_ENDPOINTS.LINE + ': ' + (err.message || 'Compruebe el servidor.')
      );
    } finally {
      isLineUploading = false;
      btnSendLine.classList.remove('loading');
      updateLineStepperUI();
    }
  });

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

  // =========================================================================
  // GESTIÓN DE MODALIDADES DE CONSULTA (LOTE vs LÍNEA A LÍNEA)
  // =========================================================================

  function setConsultMode(mode) {
    currentConsultMode = mode;
    if (mode === 'batch') {
      pillConsultBatch.classList.add('active');
      pillConsultBatch.setAttribute('aria-checked', 'true');
      pillConsultLine.classList.remove('active');
      pillConsultLine.setAttribute('aria-checked', 'false');

      consultBatchContent.style.display = 'block';
      consultLineContent.style.display = 'none';

      consultEndpointLabel.textContent = 'ENDPOINT DESTINO (LOTE)';
      consultEndpointVal.innerHTML = '<span class="http-badge get">GET</span> /files/zip &bull; application/zip';
    } else {
      pillConsultLine.classList.add('active');
      pillConsultLine.setAttribute('aria-checked', 'true');
      pillConsultBatch.classList.remove('active');
      pillConsultBatch.setAttribute('aria-checked', 'false');

      consultBatchContent.style.display = 'none';
      consultLineContent.style.display = 'block';

      consultEndpointLabel.textContent = 'ENDPOINT DESTINO (LÍNEA A LÍNEA)';
      consultEndpointVal.innerHTML = '<span class="http-badge get">GET</span> /files/consult/line?file={archivo}&index={indice} &bull; text/csv';

      // Al entrar a modo línea a línea, llamar inmediatamente a consultLineInitialize()
      consultLineInitialize();
    }
  }

  pillConsultBatch.addEventListener('click', () => setConsultMode('batch'));
  pillConsultLine.addEventListener('click', () => setConsultMode('line'));

  // LocalStorage helpers para recordar el índice por archivo
  function getStoredIndexForFile(fileName) {
    try {
      const stored = localStorage.getItem('airtortilla_consult_index_' + fileName);
      return stored !== null ? parseInt(stored, 10) : 0;
    } catch (_) {
      return 0;
    }
  }

  function setStoredIndexForFile(fileName, index) {
    try {
      localStorage.setItem('airtortilla_consult_index_' + fileName, index.toString());
    } catch (_) {}
  }

  function getCurrentConsultFile() {
    return consultFileSelect.value || 'all';
  }

  function getCurrentConsultIndex() {
    return getStoredIndexForFile(getCurrentConsultFile());
  }

  function getMaxReservesForFile(file) {
    if (!consultFileCounts || typeof consultFileCounts !== 'object') return 0;
    if (file === 'all') {
      return Object.values(consultFileCounts).reduce((sum, c) => sum + (Number(c) || 0), 0);
    }
    return Number(consultFileCounts[file]) || 0;
  }

  // 1. Cargar lista de archivos y conteo de reservas desde GET /files/consult/files
  async function fetchAvailableConsultFiles() {
    btnRefreshConsultFiles.disabled = true;
    try {
      const response = await fetch(API_ENDPOINTS.CONSULT_FILES);
      if (!response.ok) {
        throw new Error('Error al listar archivos: HTTP ' + response.status);
      }
      const data = await response.json();

      consultFileCounts = {};
      if (Array.isArray(data)) {
        data.forEach((fileName) => {
          consultFileCounts[fileName] = 0;
        });
      } else if (data && typeof data === 'object') {
        consultFileCounts = data;
      }

      const fileNames = Object.keys(consultFileCounts).sort();
      const totalAllReserves = Object.values(consultFileCounts).reduce((sum, c) => sum + (Number(c) || 0), 0);

      const currentSelected = consultFileSelect.value || 'all';
      consultFileSelect.innerHTML = '';

      // Opción 'all' con total de reservas
      const allOpt = document.createElement('option');
      allOpt.value = 'all';
      allOpt.textContent = `Todas las Reservas (all) — [${totalAllReserves} reserva${totalAllReserves === 1 ? '' : 's'}]`;
      consultFileSelect.appendChild(allOpt);

      // Opciones individuales con cantidad de reservas
      fileNames.forEach((fileName) => {
        const count = consultFileCounts[fileName] || 0;
        const opt = document.createElement('option');
        opt.value = fileName;
        opt.textContent = `${fileName} — [${count} reserva${count === 1 ? '' : 's'}]`;
        consultFileSelect.appendChild(opt);
      });

      if ([...consultFileSelect.options].some(o => o.value === currentSelected)) {
        consultFileSelect.value = currentSelected;
      } else {
        consultFileSelect.value = 'all';
      }

      showFeedback(
        consultFeedback,
        consultFeedbackIcon,
        consultFeedbackTitle,
        consultFeedbackMsg,
        'info',
        'Archivos y Reservas Disponibles',
        `Se encontraron ${fileNames.length} archivos con un total de ${totalAllReserves} reservas registradas.`
      );
    } catch (err) {
      showFeedback(
        consultFeedback,
        consultFeedbackIcon,
        consultFeedbackTitle,
        consultFeedbackMsg,
        'error',
        'Error al Obtener Archivos',
        'No se pudo conectar con ' + API_ENDPOINTS.CONSULT_FILES + ': ' + (err.message || 'Error desconocido')
      );
    } finally {
      btnRefreshConsultFiles.disabled = false;
    }
  }

  btnRefreshConsultFiles.addEventListener('click', fetchAvailableConsultFiles);

  // 2. Inicializar listas en el backend (POST /files/consult/line/initialize)
  async function consultLineInitialize() {
    consultStatusPill.textContent = 'INICIALIZANDO...';
    consultStatusPill.className = 'stepper-status-pill uploading';
    consultLineRaw.textContent = 'Inicializando listas en el backend (POST /files/consult/line/initialize)...';

    try {
      const response = await fetch(API_ENDPOINTS.CONSULT_INITIALIZE, {
        method: 'POST',
      });
      if (response.ok) {
        isConsultInitialized = true;
        consultStatusPill.textContent = 'LISTAS INICIALIZADAS';
        consultStatusPill.className = 'stepper-status-pill success';
        await fetchAvailableConsultFiles();
        updateConsultStepperDisplay();
        fetchLineRecord(getCurrentConsultFile(), getCurrentConsultIndex());
      } else {
        consultStatusPill.textContent = 'ERROR INICIALIZACIÓN';
        consultStatusPill.className = 'stepper-status-pill';
        showFeedback(
          consultFeedback,
          consultFeedbackIcon,
          consultFeedbackTitle,
          consultFeedbackMsg,
          'error',
          'Error de Inicialización',
          'El servidor respondió con código HTTP ' + response.status
        );
      }
    } catch (err) {
      consultStatusPill.textContent = 'ERROR INICIALIZACIÓN';
      consultStatusPill.className = 'stepper-status-pill';
      showFeedback(
        consultFeedback,
        consultFeedbackIcon,
        consultFeedbackTitle,
        consultFeedbackMsg,
        'error',
        'Error de Inicialización',
        'No se pudo inicializar la lectura línea a línea en ' + API_ENDPOINTS.CONSULT_INITIALIZE + ': ' + err.message
      );
    }
  }

  const initializeConsultBackend = consultLineInitialize;

  btnReinitConsult.addEventListener('click', () => consultLineInitialize());

  function updateConsultStepperDisplay() {
    const file = getCurrentConsultFile();
    const maxCount = getMaxReservesForFile(file);
    let index = getCurrentConsultIndex();

    // Si el índice supera el total disponible, ajustarlo para prevenir desbordamientos
    if (maxCount > 0 && index >= maxCount) {
      index = maxCount - 1;
      setStoredIndexForFile(file, index);
    } else if (maxCount === 0) {
      index = 0;
      setStoredIndexForFile(file, 0);
    }

    if (maxCount === 0) {
      consultIndexCounter.textContent = '0 reservas disponibles';
      btnConsultPrev.disabled = true;
      btnConsultNext.disabled = true;
      btnConsultCurrent.disabled = true;
    } else {
      consultIndexCounter.textContent = `Reserva ${index + 1} de ${maxCount}`;
      btnConsultPrev.disabled = (index <= 0) || isConsultLineLoading;
      // Prevenir clic en "Siguiente" si ya se alcanzó la última reserva
      btnConsultNext.disabled = (index >= maxCount - 1) || isConsultLineLoading;
      btnConsultCurrent.disabled = isConsultLineLoading;
    }
  }

  consultFileSelect.addEventListener('change', () => {
    updateConsultStepperDisplay();
    const file = getCurrentConsultFile();
    const index = getCurrentConsultIndex();
    fetchLineRecord(file, index);
  });

  btnResetConsultIndex.addEventListener('click', () => {
    const file = getCurrentConsultFile();
    setStoredIndexForFile(file, 0);
    updateConsultStepperDisplay();
    fetchLineRecord(file, 0);
  });

  // 3. Consultar una línea específica: GET /files/consult/line?file={file}&index={index}
  async function fetchLineRecord(file, index) {
    if (isConsultLineLoading) return;

    if (!isConsultInitialized) {
      await consultLineInitialize();
      return;
    }

    const maxCount = getMaxReservesForFile(file);
    if (maxCount === 0) {
      consultStatusPill.textContent = 'SIN RESERVAS';
      consultStatusPill.className = 'stepper-status-pill';
      consultLineRaw.textContent = `— No hay reservas registradas en "${file}" —`;
      clearConsultFields();
      updateConsultStepperDisplay();
      return;
    }

    if (index >= maxCount) {
      index = maxCount - 1;
      setStoredIndexForFile(file, index);
    }

    isConsultLineLoading = true;
    btnConsultCurrent.disabled = true;
    btnConsultNext.disabled = true;
    btnConsultPrev.disabled = true;
    btnConsultCurrent.classList.add('loading');
    btnConsultCurrentText.textContent = 'Consultando...';
    consultStatusPill.textContent = 'CONSULTANDO...';
    consultStatusPill.className = 'stepper-status-pill uploading';

    const url = `${API_ENDPOINTS.CONSULT_LINE}?file=${encodeURIComponent(file)}&index=${index}`;

    try {
      const response = await fetch(url, {
        method: 'GET',
        headers: {
          'Accept': 'text/csv',
        },
      });

      if (response.status === 204) {
        // No Content: No hay más reservas
        consultStatusPill.textContent = 'NO HAY MÁS RESERVAS';
        consultStatusPill.className = 'stepper-status-pill completed';
        consultLineRaw.textContent = `— No hay más reservas disponibles en "${file}" —`;
        clearConsultFields();
        showFeedback(
          consultFeedback,
          consultFeedbackIcon,
          consultFeedbackTitle,
          consultFeedbackMsg,
          'info',
          'Sin Más Reservas [HTTP 204 No Content]',
          `No hay más reservas disponibles.`
        );
      } else if (response.ok) {
        const csvLine = await response.text();
        consultStatusPill.textContent = 'LÍNEA CARGADA';
        consultStatusPill.className = 'stepper-status-pill success';
        consultLineRaw.textContent = csvLine.trim();

        // Renderizar los campos parseados
        parseAndDisplayConsultLine(csvLine.trim());

        const maxCount = getMaxReservesForFile(file);
        showFeedback(
          consultFeedback,
          consultFeedbackIcon,
          consultFeedbackTitle,
          consultFeedbackMsg,
          'success',
          `Reserva Consultada (${index + 1} de ${maxCount}) [HTTP 200]`,
          csvLine.trim()
        );
      } else {
        const errorText = await response.text();
        consultStatusPill.textContent = 'ERROR ' + response.status;
        consultStatusPill.className = 'stepper-status-pill';
        showFeedback(
          consultFeedback,
          consultFeedbackIcon,
          consultFeedbackTitle,
          consultFeedbackMsg,
          'error',
          `Error en Consulta de Línea [HTTP ${response.status}]`,
          errorText || `No se pudo obtener el índice ${index} para "${file}". Si se modificaron archivos, reinicialice las listas.`
        );
      }
    } catch (err) {
      consultStatusPill.textContent = 'ERROR DE RED';
      consultStatusPill.className = 'stepper-status-pill';
      showFeedback(
        consultFeedback,
        consultFeedbackIcon,
        consultFeedbackTitle,
        consultFeedbackMsg,
        'error',
        'Error de Red',
        'No se pudo conectar con ' + url + ': ' + (err.message || 'Error de conexión.')
      );
    } finally {
      isConsultLineLoading = false;
      btnConsultCurrent.classList.remove('loading');
      btnConsultCurrent.disabled = false;
      btnConsultNext.disabled = false;
      btnConsultCurrentText.textContent = 'Consultar Línea';
      updateConsultStepperDisplay();
    }
  }

  function parseAndDisplayConsultLine(csvText) {
    if (!csvText) {
      clearConsultFields();
      return;
    }
    const cols = csvText.split(',').map(c => c.trim());
    // Formato retornado por Reserva.getCsv():
    // 0: identifier (ej. AT-0021)
    // 1: destino
    // 2: origin
    // 3: flightNumber
    // 4: nombre
    // 5: email
    // 6: edad
    // 7: documento
    // 8: asiento
    cfieldId.textContent = cols[0] || '—';
    cfieldDestino.textContent = cols[1] || '—';
    cfieldOrigin.textContent = cols[2] || '—';
    cfieldFlight.textContent = cols[3] || '—';
    cfieldNombre.textContent = cols[4] || '—';
    cfieldEmail.textContent = cols[5] || '—';
    cfieldEdad.textContent = cols[6] || '—';

    const doc = cols[7] || '';
    const seat = cols[8] || '';
    cfieldDocSeat.textContent = (doc && seat) ? `${doc} / ${seat}` : (doc || seat || '—');
  }

  function clearConsultFields() {
    cfieldId.textContent = '—';
    cfieldDestino.textContent = '—';
    cfieldOrigin.textContent = '—';
    cfieldFlight.textContent = '—';
    cfieldNombre.textContent = '—';
    cfieldEmail.textContent = '—';
    cfieldEdad.textContent = '—';
    cfieldDocSeat.textContent = '—';
  }

  // Navegación de botones
  btnConsultCurrent.addEventListener('click', () => {
    fetchLineRecord(getCurrentConsultFile(), getCurrentConsultIndex());
  });

  btnConsultNext.addEventListener('click', () => {
    const file = getCurrentConsultFile();
    const maxCount = getMaxReservesForFile(file);
    const currentIndex = getCurrentConsultIndex();
    // Bloquear avance si ya se alcanzó la última reserva disponible para evitar excepciones
    if (maxCount > 0 && currentIndex + 1 < maxCount) {
      const nextIndex = currentIndex + 1;
      setStoredIndexForFile(file, nextIndex);
      updateConsultStepperDisplay();
      fetchLineRecord(file, nextIndex);
    } else {
      updateConsultStepperDisplay();
    }
  });

  btnConsultPrev.addEventListener('click', () => {
    const file = getCurrentConsultFile();
    const currentIndex = getCurrentConsultIndex();
    if (currentIndex > 0) {
      const prevIndex = currentIndex - 1;
      setStoredIndexForFile(file, prevIndex);
      updateConsultStepperDisplay();
      fetchLineRecord(file, prevIndex);
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
