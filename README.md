# Biblioteca Digital — gRPC com Java

Backend distribuído de um **Sistema de Gerenciamento de Biblioteca Digital**, desenvolvido em **Java 17 + Maven + gRPC 1.68 + Protocol Buffers 3** para a disciplina de Computação Distribuída.

O servidor gRPC central expõe o serviço `BibliotecaService`, que implementa os quatro tipos de comunicação do gRPC:

| RPC | Tipo | Descrição |
|---|---|---|
| `cadastrarLivro` | Unary | Cadastra um livro (título, autor, ano, ISBN) e retorna o ID gerado e o status |
| `listarLivrosPorAutor` | Server Streaming | Retorna em stream todos os livros de um autor |
| `registrarEmprestimos` | Client Streaming | Recebe um stream de empréstimos (usuário + livro_id) e responde com um resumo (total registrado e tempo de processamento) |
| `chatBibliotecario` | Bidirectional Streaming | Para cada mensagem do usuário, responde com uma sugestão de livro relacionada à palavra-chave |

A persistência é feita em memória (`ConcurrentHashMap` / `List`).

## Integrantes

| Nome | RA |
|---|---|
| Caio Ariel Cardoso Saraiva | 10439611 |
| Isabela Hissa Pinto | 10441873 |
| Kaique Barros Paiva | 10441787 |

## Estrutura do projeto

```text
biblioteca-grpc/
├── pom.xml
└── src/main/
    ├── java/br/mackenzie/biblioteca/
    │   ├── server/
    │   │   ├── BibliotecaServiceImpl.java   # implementação dos 4 RPCs
    │   │   └── ServidorBiblioteca.java      # inicialização do servidor (porta 50051)
    │   ├── client/
    │   │   └── ClienteBiblioteca.java       # roteiro de testes dos 4 RPCs
    │   └── model/
    │       ├── Acervo.java                  # armazenamento em memória
    │       ├── LivroEntity.java
    │       └── EmprestimoEntity.java
    └── proto/
        └── biblioteca.proto                 # contrato do serviço
```

## Tratamento de erros

Os erros são retornados com `io.grpc.Status`:

| Situação | Status |
|---|---|
| Título, autor ou ISBN vazios / ano inválido no cadastro | `INVALID_ARGUMENT` |
| ISBN já cadastrado | `ALREADY_EXISTS` |
| Nome do autor vazio na listagem | `INVALID_ARGUMENT` |
| Autor sem livros cadastrados | `NOT_FOUND` |
| Empréstimo com usuário vazio | `INVALID_ARGUMENT` |
| Empréstimo de livro inexistente | `NOT_FOUND` |
| Mensagem vazia no chat | `INVALID_ARGUMENT` |

O servidor imprime no console cada chamada recebida, com o método e os parâmetros.

## Como compilar e executar

Pré-requisitos: **Java 17+** e **Maven**.

Compilar (gera também as classes a partir do `.proto`):

```bash
mvn clean compile
```

Empacotar:

```bash
mvn clean package
```

Iniciar o servidor (terminal 1):

```bash
mvn exec:java -Dexec.mainClass=br.mackenzie.biblioteca.server.ServidorBiblioteca
```

Executar o cliente (terminal 2):

```bash
mvn exec:java -Dexec.mainClass=br.mackenzie.biblioteca.client.ClienteBiblioteca
```

O cliente aceita opcionalmente host e porta: `-Dexec.args="localhost 50051"`.

## Roteiro de testes

O `ClienteBiblioteca` executa, em sequência:

1. Cadastro de 3 livros (Unary)
2. Listagem dos livros de um autor cadastrado (Server Streaming)
3. Listagem de um autor inexistente — retorna `NOT_FOUND` (Server Streaming)
4. Registro de 5 empréstimos consecutivos (Client Streaming)
5. Chat com 5 mensagens (Bidirectional Streaming)
6. Cadastro com ISBN duplicado — retorna `ALREADY_EXISTS` (Unary)

## Saída esperada

### Cliente

```text
==================================================
1. Cadastrar 3 livros (Unary)
==================================================
Cadastrado: 'Redes de Computadores' -> ID 1 | Livro cadastrado com sucesso
Cadastrado: 'Sistemas Operacionais Modernos' -> ID 2 | Livro cadastrado com sucesso
Cadastrado: 'Dom Casmurro' -> ID 3 | Livro cadastrado com sucesso

==================================================
2. Listar livros de autor cadastrado (Server Streaming)
==================================================
Buscando livros do autor: Andrew S. Tanenbaum
  [ID 1] Redes de Computadores - Andrew S. Tanenbaum (2011) ISBN 978-8576059240
  [ID 2] Sistemas Operacionais Modernos - Andrew S. Tanenbaum (2015) ISBN 978-8543005676
Total recebido: 2

==================================================
3. Listar livros de autor inexistente (Server Streaming)
==================================================
Buscando livros do autor: Autor Desconhecido
Erro gRPC: NOT_FOUND - Nenhum livro encontrado para o autor Autor Desconhecido

==================================================
4. Registrar 5 emprestimos (Client Streaming)
==================================================
Enviando emprestimo: usuario=caio, livro_id=1
Enviando emprestimo: usuario=isabela, livro_id=2
Enviando emprestimo: usuario=kaique, livro_id=3
Enviando emprestimo: usuario=caio, livro_id=2
Enviando emprestimo: usuario=isabela, livro_id=3
Resumo: 5 emprestimos registrados em 503 ms

==================================================
5. Chat com o bibliotecario (Bidirectional Streaming)
==================================================
caio: Gostaria de aprender java
Bibliotecario [java]: Recomendo: Effective Java - Joshua Bloch
caio: Tem algo sobre sistemas distribuidos?
Bibliotecario [distribuido]: Recomendo: Sistemas Distribuidos: Conceitos e Projeto - George Coulouris
caio: Quero ler sobre sistemas operacionais
Bibliotecario [sistemas]: Temos no acervo: Sistemas Operacionais Modernos - Andrew S. Tanenbaum (ID 2)
caio: Indique um romance brasileiro
Bibliotecario [romance]: Recomendo: Dom Casmurro - Machado de Assis
caio: Algo sobre astronomia
Bibliotecario [Algo sobre astronomia]: Nao encontrei sugestoes para essa palavra-chave. Tente: java, distribuido, redes, algoritmos, fantasia, romance, ficcao, historia
Chat encerrado pelo servidor

==================================================
6. Cadastrar livro com ISBN duplicado (Unary - ALREADY_EXISTS)
==================================================
Erro gRPC: ALREADY_EXISTS - Ja existe um livro cadastrado com o ISBN 978-8576059240
```

### Servidor

```text
Servidor da Biblioteca iniciado na porta 50051
[18:03:22.927] cadastrarLivro -> titulo='Redes de Computadores', autor='Andrew S. Tanenbaum', ano=2011, isbn='978-8576059240'
[18:03:22.951] cadastrarLivro -> titulo='Sistemas Operacionais Modernos', autor='Andrew S. Tanenbaum', ano=2015, isbn='978-8543005676'
[18:03:22.955] cadastrarLivro -> titulo='Dom Casmurro', autor='Machado de Assis', ano=1899, isbn='978-8535910667'
[18:03:22.960] listarLivrosPorAutor -> autor='Andrew S. Tanenbaum'
[18:03:22.971] listarLivrosPorAutor -> autor='Autor Desconhecido'
[18:03:22.977] registrarEmprestimos -> stream iniciado
[18:03:22.979] registrarEmprestimos -> usuario='caio', livro_id=1
[18:03:23.079] registrarEmprestimos -> usuario='isabela', livro_id=2
[18:03:23.180] registrarEmprestimos -> usuario='kaique', livro_id=3
[18:03:23.280] registrarEmprestimos -> usuario='caio', livro_id=2
[18:03:23.381] registrarEmprestimos -> usuario='isabela', livro_id=3
[18:03:23.481] registrarEmprestimos -> stream finalizado: total=5, tempo=503ms
[18:03:23.489] chatBibliotecario -> chat iniciado
[18:03:23.491] chatBibliotecario -> usuario='caio', texto='Gostaria de aprender java'
[18:03:23.791] chatBibliotecario -> usuario='caio', texto='Tem algo sobre sistemas distribuidos?'
[18:03:24.091] chatBibliotecario -> usuario='caio', texto='Quero ler sobre sistemas operacionais'
[18:03:24.392] chatBibliotecario -> usuario='caio', texto='Indique um romance brasileiro'
[18:03:24.692] chatBibliotecario -> usuario='caio', texto='Algo sobre astronomia'
[18:03:24.992] chatBibliotecario -> chat encerrado
[18:03:24.995] cadastrarLivro -> titulo='Redes de Computadores (copia)', autor='Andrew S. Tanenbaum', ano=2011, isbn='978-8576059240'
```
