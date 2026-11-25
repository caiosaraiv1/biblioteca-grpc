package br.mackenzie.biblioteca.server;

import io.grpc.Server;
import io.grpc.ServerBuilder;

import java.io.IOException;

public class ServidorBiblioteca {

    private static final int PORTA = 50051;

    public static void main(String[] args) throws IOException, InterruptedException {
        Server server = ServerBuilder.forPort(PORTA)
                .addService(new BibliotecaServiceImpl())
                .build()
                .start();

        System.out.println("Servidor da Biblioteca iniciado na porta " + PORTA);

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("Encerrando servidor...");
            server.shutdown();
        }));

        server.awaitTermination();
    }
}
