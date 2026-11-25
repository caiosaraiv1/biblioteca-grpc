package br.mackenzie.biblioteca.client;

import br.mackenzie.biblioteca.grpc.AutorRequest;
import br.mackenzie.biblioteca.grpc.BibliotecaServiceGrpc;
import br.mackenzie.biblioteca.grpc.CadastroResponse;
import br.mackenzie.biblioteca.grpc.EmprestimoRequest;
import br.mackenzie.biblioteca.grpc.Livro;
import br.mackenzie.biblioteca.grpc.LivroRequest;
import br.mackenzie.biblioteca.grpc.MensagemUsuario;
import br.mackenzie.biblioteca.grpc.ResumoEmprestimos;
import br.mackenzie.biblioteca.grpc.SugestaoLivro;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.StreamObserver;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class ClienteBiblioteca {

    private final BibliotecaServiceGrpc.BibliotecaServiceBlockingStub blockingStub;
    private final BibliotecaServiceGrpc.BibliotecaServiceStub asyncStub;

    public ClienteBiblioteca(ManagedChannel channel) {
        this.blockingStub = BibliotecaServiceGrpc.newBlockingStub(channel);
        this.asyncStub = BibliotecaServiceGrpc.newStub(channel);
    }

    public static void main(String[] args) throws InterruptedException {
        String host = args.length > 0 ? args[0] : "localhost";
        int porta = args.length > 1 ? Integer.parseInt(args[1]) : 50051;

        ManagedChannel channel = ManagedChannelBuilder.forAddress(host, porta)
                .usePlaintext()
                .build();

        try {
            ClienteBiblioteca cliente = new ClienteBiblioteca(channel);

            titulo("1. Cadastrar 3 livros (Unary)");
            List<Long> ids = new ArrayList<>();
            ids.add(cliente.cadastrarLivro("Redes de Computadores", "Andrew S. Tanenbaum", 2011, "978-8576059240"));
            ids.add(cliente.cadastrarLivro("Sistemas Operacionais Modernos", "Andrew S. Tanenbaum", 2015, "978-8543005676"));
            ids.add(cliente.cadastrarLivro("Dom Casmurro", "Machado de Assis", 1899, "978-8535910667"));

            titulo("2. Listar livros de autor cadastrado (Server Streaming)");
            cliente.listarLivrosPorAutor("Andrew S. Tanenbaum");

            titulo("3. Listar livros de autor inexistente (Server Streaming)");
            cliente.listarLivrosPorAutor("Autor Desconhecido");

            titulo("4. Registrar 5 emprestimos (Client Streaming)");
            List<EmprestimoRequest> emprestimos = List.of(
                    emprestimo("caio", ids.get(0)),
                    emprestimo("isabela", ids.get(1)),
                    emprestimo("kaique", ids.get(2)),
                    emprestimo("caio", ids.get(1)),
                    emprestimo("isabela", ids.get(2)));
            cliente.registrarEmprestimos(emprestimos);

            titulo("5. Chat com o bibliotecario (Bidirectional Streaming)");
            cliente.chatBibliotecario("caio", List.of(
                    "Gostaria de aprender java",
                    "Tem algo sobre sistemas distribuidos?",
                    "Quero ler sobre sistemas operacionais",
                    "Indique um romance brasileiro",
                    "Algo sobre astronomia"));

            titulo("6. Cadastrar livro com ISBN duplicado (Unary - ALREADY_EXISTS)");
            cliente.cadastrarLivro("Redes de Computadores (copia)", "Andrew S. Tanenbaum", 2011, "978-8576059240");
        } finally {
            channel.shutdown().awaitTermination(5, TimeUnit.SECONDS);
        }
    }

    private static void titulo(String texto) {
        System.out.println();
        System.out.println("==================================================");
        System.out.println(texto);
        System.out.println("==================================================");
    }

    private static EmprestimoRequest emprestimo(String usuario, long livroId) {
        return EmprestimoRequest.newBuilder().setUsuario(usuario).setLivroId(livroId).build();
    }

    private static void imprimirErro(Throwable t) {
        Status status = Status.fromThrowable(t);
        System.out.println("Erro gRPC: " + status.getCode() + " - " + status.getDescription());
    }

    public long cadastrarLivro(String titulo, String autor, int ano, String isbn) {
        LivroRequest request = LivroRequest.newBuilder()
                .setTitulo(titulo)
                .setAutor(autor)
                .setAno(ano)
                .setIsbn(isbn)
                .build();
        try {
            CadastroResponse response = blockingStub.cadastrarLivro(request);
            System.out.println("Cadastrado: '" + titulo + "' -> ID " + response.getId() + " | " + response.getStatus());
            return response.getId();
        } catch (StatusRuntimeException e) {
            imprimirErro(e);
            return -1;
        }
    }

    public void listarLivrosPorAutor(String autor) {
        System.out.println("Buscando livros do autor: " + autor);
        try {
            Iterator<Livro> livros = blockingStub.listarLivrosPorAutor(
                    AutorRequest.newBuilder().setAutor(autor).build());
            int total = 0;
            while (livros.hasNext()) {
                Livro livro = livros.next();
                total++;
                System.out.printf("  [ID %d] %s - %s (%d) ISBN %s%n",
                        livro.getId(), livro.getTitulo(), livro.getAutor(), livro.getAno(), livro.getIsbn());
            }
            System.out.println("Total recebido: " + total);
        } catch (StatusRuntimeException e) {
            imprimirErro(e);
        }
    }

    public void registrarEmprestimos(List<EmprestimoRequest> emprestimos) throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);

        StreamObserver<ResumoEmprestimos> respostaObserver = new StreamObserver<>() {
            @Override
            public void onNext(ResumoEmprestimos resumo) {
                System.out.println("Resumo: " + resumo.getTotalRegistrados() + " emprestimos registrados em "
                        + resumo.getTempoProcessamentoMs() + " ms");
            }

            @Override
            public void onError(Throwable t) {
                imprimirErro(t);
                latch.countDown();
            }

            @Override
            public void onCompleted() {
                latch.countDown();
            }
        };

        StreamObserver<EmprestimoRequest> requestObserver = asyncStub.registrarEmprestimos(respostaObserver);
        try {
            for (EmprestimoRequest emprestimo : emprestimos) {
                System.out.println("Enviando emprestimo: usuario=" + emprestimo.getUsuario()
                        + ", livro_id=" + emprestimo.getLivroId());
                requestObserver.onNext(emprestimo);
                Thread.sleep(100);
            }
        } catch (RuntimeException e) {
            requestObserver.onError(e);
            throw e;
        }
        requestObserver.onCompleted();

        latch.await(10, TimeUnit.SECONDS);
    }

    public void chatBibliotecario(String usuario, List<String> mensagens) throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);

        StreamObserver<SugestaoLivro> respostaObserver = new StreamObserver<>() {
            @Override
            public void onNext(SugestaoLivro sugestao) {
                System.out.println("Bibliotecario [" + sugestao.getPalavraChave() + "]: " + sugestao.getSugestao());
            }

            @Override
            public void onError(Throwable t) {
                imprimirErro(t);
                latch.countDown();
            }

            @Override
            public void onCompleted() {
                System.out.println("Chat encerrado pelo servidor");
                latch.countDown();
            }
        };

        StreamObserver<MensagemUsuario> requestObserver = asyncStub.chatBibliotecario(respostaObserver);
        try {
            for (String texto : mensagens) {
                System.out.println(usuario + ": " + texto);
                requestObserver.onNext(MensagemUsuario.newBuilder().setUsuario(usuario).setTexto(texto).build());
                Thread.sleep(300);
            }
        } catch (RuntimeException e) {
            requestObserver.onError(e);
            throw e;
        }
        requestObserver.onCompleted();

        latch.await(10, TimeUnit.SECONDS);
    }
}
