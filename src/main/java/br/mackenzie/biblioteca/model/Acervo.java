package br.mackenzie.biblioteca.model;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

public class Acervo {

    private final Map<Long, LivroEntity> livros = new ConcurrentHashMap<>();
    private final Map<String, Long> indicePorIsbn = new ConcurrentHashMap<>();
    private final List<EmprestimoEntity> emprestimos = Collections.synchronizedList(new ArrayList<>());
    private final AtomicLong sequenciaLivro = new AtomicLong(0);
    private final AtomicLong sequenciaEmprestimo = new AtomicLong(0);

    public synchronized Optional<LivroEntity> adicionarLivro(String titulo, String autor, int ano, String isbn) {
        if (indicePorIsbn.containsKey(isbn)) {
            return Optional.empty();
        }
        long id = sequenciaLivro.incrementAndGet();
        LivroEntity livro = new LivroEntity(id, titulo, autor, ano, isbn);
        livros.put(id, livro);
        indicePorIsbn.put(isbn, id);
        return Optional.of(livro);
    }

    public List<LivroEntity> buscarPorAutor(String autor) {
        return livros.values().stream()
                .filter(l -> l.autor().equalsIgnoreCase(autor.trim()))
                .sorted((a, b) -> Long.compare(a.id(), b.id()))
                .toList();
    }

    public List<LivroEntity> buscarPorTermo(String termo) {
        String t = termo.toLowerCase();
        return livros.values().stream()
                .filter(l -> l.titulo().toLowerCase().contains(t) || l.autor().toLowerCase().contains(t))
                .sorted((a, b) -> Long.compare(a.id(), b.id()))
                .toList();
    }

    public boolean existeLivro(long id) {
        return livros.containsKey(id);
    }

    public EmprestimoEntity registrarEmprestimo(String usuario, long livroId) {
        EmprestimoEntity emprestimo = new EmprestimoEntity(
                sequenciaEmprestimo.incrementAndGet(), usuario, livroId, LocalDateTime.now());
        emprestimos.add(emprestimo);
        return emprestimo;
    }
}
