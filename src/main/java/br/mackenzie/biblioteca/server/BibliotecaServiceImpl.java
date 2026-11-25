package br.mackenzie.biblioteca.server;

import br.mackenzie.biblioteca.grpc.AutorRequest;
import br.mackenzie.biblioteca.grpc.BibliotecaServiceGrpc;
import br.mackenzie.biblioteca.grpc.CadastroResponse;
import br.mackenzie.biblioteca.grpc.EmprestimoRequest;
import br.mackenzie.biblioteca.grpc.Livro;
import br.mackenzie.biblioteca.grpc.LivroRequest;
import br.mackenzie.biblioteca.grpc.MensagemUsuario;
import br.mackenzie.biblioteca.grpc.ResumoEmprestimos;
import br.mackenzie.biblioteca.grpc.SugestaoLivro;
import br.mackenzie.biblioteca.model.Acervo;
import br.mackenzie.biblioteca.model.LivroEntity;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class BibliotecaServiceImpl extends BibliotecaServiceGrpc.BibliotecaServiceImplBase {

    private static final DateTimeFormatter FORMATO_HORA = DateTimeFormatter.ofPattern("HH:mm:ss.SSS");

    private final Acervo acervo = new Acervo();
    private final Map<String, String> sugestoes = new LinkedHashMap<>();

    public BibliotecaServiceImpl() {
        sugestoes.put("java", "Effective Java - Joshua Bloch");
        sugestoes.put("distribuido", "Sistemas Distribuidos: Conceitos e Projeto - George Coulouris");
        sugestoes.put("redes", "Redes de Computadores - Andrew S. Tanenbaum");
        sugestoes.put("algoritmos", "Algoritmos: Teoria e Pratica - Thomas H. Cormen");
        sugestoes.put("fantasia", "O Senhor dos Aneis - J. R. R. Tolkien");
        sugestoes.put("romance", "Dom Casmurro - Machado de Assis");
        sugestoes.put("ficcao", "Duna - Frank Herbert");
        sugestoes.put("historia", "Sapiens: Uma Breve Historia da Humanidade - Yuval Noah Harari");
    }

    private void log(String metodo, String parametros) {
        System.out.printf("[%s] %s -> %s%n", LocalDateTime.now().format(FORMATO_HORA), metodo, parametros);
    }

    private static boolean vazio(String valor) {
        return valor == null || valor.isBlank();
    }

    private static Livro paraMensagem(LivroEntity livro) {
        return Livro.newBuilder()
                .setId(livro.id())
                .setTitulo(livro.titulo())
                .setAutor(livro.autor())
                .setAno(livro.ano())
                .setIsbn(livro.isbn())
                .build();
    }

    @Override
    public void cadastrarLivro(LivroRequest request, StreamObserver<CadastroResponse> responseObserver) {
        log("cadastrarLivro", String.format("titulo='%s', autor='%s', ano=%d, isbn='%s'",
                request.getTitulo(), request.getAutor(), request.getAno(), request.getIsbn()));

        if (vazio(request.getTitulo()) || vazio(request.getAutor()) || vazio(request.getIsbn())) {
            responseObserver.onError(Status.INVALID_ARGUMENT
                    .withDescription("Titulo, autor e ISBN sao obrigatorios")
                    .asRuntimeException());
            return;
        }

        int anoAtual = LocalDateTime.now().getYear();
        if (request.getAno() <= 0 || request.getAno() > anoAtual) {
            responseObserver.onError(Status.INVALID_ARGUMENT
                    .withDescription("Ano invalido: " + request.getAno())
                    .asRuntimeException());
            return;
        }

        Optional<LivroEntity> cadastrado = acervo.adicionarLivro(
                request.getTitulo().trim(), request.getAutor().trim(), request.getAno(), request.getIsbn().trim());

        if (cadastrado.isEmpty()) {
            responseObserver.onError(Status.ALREADY_EXISTS
                    .withDescription("Ja existe um livro cadastrado com o ISBN " + request.getIsbn())
                    .asRuntimeException());
            return;
        }

        responseObserver.onNext(CadastroResponse.newBuilder()
                .setId(cadastrado.get().id())
                .setStatus("Livro cadastrado com sucesso")
                .build());
        responseObserver.onCompleted();
    }

    @Override
    public void listarLivrosPorAutor(AutorRequest request, StreamObserver<Livro> responseObserver) {
        log("listarLivrosPorAutor", String.format("autor='%s'", request.getAutor()));

        if (vazio(request.getAutor())) {
            responseObserver.onError(Status.INVALID_ARGUMENT
                    .withDescription("O nome do autor e obrigatorio")
                    .asRuntimeException());
            return;
        }

        List<LivroEntity> livros = acervo.buscarPorAutor(request.getAutor());

        if (livros.isEmpty()) {
            responseObserver.onError(Status.NOT_FOUND
                    .withDescription("Nenhum livro encontrado para o autor " + request.getAutor())
                    .asRuntimeException());
            return;
        }

        for (LivroEntity livro : livros) {
            responseObserver.onNext(paraMensagem(livro));
        }
        responseObserver.onCompleted();
    }

    @Override
    public StreamObserver<EmprestimoRequest> registrarEmprestimos(StreamObserver<ResumoEmprestimos> responseObserver) {
        log("registrarEmprestimos", "stream iniciado");
        long inicio = System.nanoTime();

        return new StreamObserver<>() {
            private int total = 0;
            private Status erro = null;

            @Override
            public void onNext(EmprestimoRequest emprestimo) {
                log("registrarEmprestimos", String.format("usuario='%s', livro_id=%d",
                        emprestimo.getUsuario(), emprestimo.getLivroId()));

                if (erro != null) {
                    return;
                }
                if (vazio(emprestimo.getUsuario())) {
                    erro = Status.INVALID_ARGUMENT.withDescription("Usuario do emprestimo e obrigatorio");
                    return;
                }
                if (!acervo.existeLivro(emprestimo.getLivroId())) {
                    erro = Status.NOT_FOUND.withDescription("Livro com ID " + emprestimo.getLivroId() + " nao encontrado");
                    return;
                }
                acervo.registrarEmprestimo(emprestimo.getUsuario().trim(), emprestimo.getLivroId());
                total++;
            }

            @Override
            public void onError(Throwable t) {
                log("registrarEmprestimos", "erro no stream do cliente: " + t.getMessage());
            }

            @Override
            public void onCompleted() {
                long tempoMs = (System.nanoTime() - inicio) / 1_000_000;
                log("registrarEmprestimos", String.format("stream finalizado: total=%d, tempo=%dms", total, tempoMs));

                if (erro != null) {
                    responseObserver.onError(erro.asRuntimeException());
                    return;
                }
                responseObserver.onNext(ResumoEmprestimos.newBuilder()
                        .setTotalRegistrados(total)
                        .setTempoProcessamentoMs(tempoMs)
                        .build());
                responseObserver.onCompleted();
            }
        };
    }

    @Override
    public StreamObserver<MensagemUsuario> chatBibliotecario(StreamObserver<SugestaoLivro> responseObserver) {
        log("chatBibliotecario", "chat iniciado");

        return new StreamObserver<>() {
            @Override
            public void onNext(MensagemUsuario mensagem) {
                log("chatBibliotecario", String.format("usuario='%s', texto='%s'",
                        mensagem.getUsuario(), mensagem.getTexto()));

                if (vazio(mensagem.getTexto())) {
                    responseObserver.onError(Status.INVALID_ARGUMENT
                            .withDescription("A mensagem nao pode ser vazia")
                            .asRuntimeException());
                    return;
                }

                responseObserver.onNext(gerarSugestao(mensagem.getTexto()));
            }

            @Override
            public void onError(Throwable t) {
                log("chatBibliotecario", "erro no stream do cliente: " + t.getMessage());
            }

            @Override
            public void onCompleted() {
                log("chatBibliotecario", "chat encerrado");
                responseObserver.onCompleted();
            }
        };
    }

    private SugestaoLivro gerarSugestao(String texto) {
        String normalizado = texto.toLowerCase();

        for (Map.Entry<String, String> entrada : sugestoes.entrySet()) {
            if (normalizado.contains(entrada.getKey())) {
                return SugestaoLivro.newBuilder()
                        .setPalavraChave(entrada.getKey())
                        .setSugestao("Recomendo: " + entrada.getValue())
                        .build();
            }
        }

        for (String palavra : normalizado.split("\\s+")) {
            if (palavra.length() < 3) {
                continue;
            }
            List<LivroEntity> encontrados = acervo.buscarPorTermo(palavra);
            if (!encontrados.isEmpty()) {
                LivroEntity livro = encontrados.get(0);
                return SugestaoLivro.newBuilder()
                        .setPalavraChave(palavra)
                        .setSugestao("Temos no acervo: " + livro.titulo() + " - " + livro.autor() + " (ID " + livro.id() + ")")
                        .build();
            }
        }

        return SugestaoLivro.newBuilder()
                .setPalavraChave(texto)
                .setSugestao("Nao encontrei sugestoes para essa palavra-chave. Tente: " + String.join(", ", sugestoes.keySet()))
                .build();
    }
}
