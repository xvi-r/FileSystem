package com.example.demo.model;

public class Person {
    final private String nombre;
    final private String email;
    final private int edad;
    final private String documento;
    private String asiento;

    @Override
    public String toString() {
        return "Person{" +
                "nombre='" + nombre + '\'' +
                ", email='" + email + '\'' +
                ", edad=" + edad +
                ", documento='" + documento + '\'' +
                ", asiento='" + asiento + '\'' +
                '}';
    }

    public Person(String nombre, String edad, String documento, String asiento, String email) {
        this.nombre = nombre;
        this.edad = Integer.parseInt(edad);
        this.documento = documento;
        this.asiento = asiento;
        this.email = email;
    }

    public String getAsiento() {
        return asiento;
    }


    public void setAsiento(String asiento) {
        this.asiento = asiento;
    }

    public String getEmail() {
        return email;
    }

    public String getNombre() {
        return nombre;
    }

    public int getEdad() {
        return edad;
    }

    public String getDocumento() {
        return documento;
    }

    public String getCsvString() {
        return String.format(
                "%s,%s,%d,%s,%s",
                nombre,
                email,
                edad,
                documento,
                asiento
        );
    }
}
