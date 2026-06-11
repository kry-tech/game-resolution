package com.gameresolution;

public class Perfil {
    String id;
    String nome;
    String pacote;
    int largura;
    int altura;
    int dpi;
    boolean ativo;

    public Perfil(String id, String nome, String pacote, int largura, int altura, int dpi) {
        this.id = id;
        this.nome = nome;
        this.pacote = pacote;
        this.largura = largura;
        this.altura = altura;
        this.dpi = dpi;
        this.ativo = false;
    }
}
