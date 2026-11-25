package br.mackenzie.biblioteca.model;

import java.time.LocalDateTime;

public record EmprestimoEntity(long id, String usuario, long livroId, LocalDateTime dataEmprestimo) {
}
