import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Exercicio 3 - Servidor que recebe imagens binarias e as guarda em disco
 * (porta 9003).
 *
 * Protocolo combinado com o cliente, em cima do TCP:
 *
 *   1. UTF   -> nome do arquivo        (DataOutputStream.writeUTF)
 *   2. LONG  -> tamanho em bytes       (DataOutputStream.writeLong)
 *   3. BYTES -> o conteudo binario da imagem
 *   ... repete 1-3 para cada imagem enviada ...
 *
 * Enviar o tamanho antes dos bytes e essencial: o TCP e um fluxo continuo, sem
 * fronteira entre "mensagens", logo o servidor precisa saber exatamente quantos
 * bytes ler para nao misturar uma imagem com a proxima.
 *
 * Execucao:
 *   javac ServidorImagens.java && java ServidorImagens
 */
public class ServidorImagens {

    private static final int PORTA = 9003;
    private static final Path PASTA_DESTINO = Paths.get("imagens_recebidas");

    public static void main(String[] args) {
        try {
            // Cria a pasta onde as imagens serao gravadas, se ainda nao existir.
            Files.createDirectories(PASTA_DESTINO);
        } catch (IOException e) {
            System.err.println("[SERVIDOR] Nao foi possivel criar a pasta de destino: "
                    + e.getMessage());
            return;
        }

        try (ServerSocket servidor = new ServerSocket(PORTA)) {
            System.out.println("[SERVIDOR] Escutando na porta " + PORTA + ".");
            System.out.println("[SERVIDOR] Salvando em: " + PASTA_DESTINO.toAbsolutePath() + "\n");

            // Atende um cliente por vez, em sua propria thread, e volta a
            // aceitar novas conexoes.
            while (true) {
                Socket cliente = servidor.accept();
                new Thread(() -> atender(cliente)).start();
            }
        } catch (IOException e) {
            System.err.println("[SERVIDOR] Erro: " + e.getMessage());
        }
    }

    /** Recebe todas as imagens que um cliente enviar e grava cada uma em disco. */
    private static void atender(Socket cliente) {
        String origem = cliente.getInetAddress().getHostAddress() + ":" + cliente.getPort();
        System.out.println("[SERVIDOR] Cliente conectado: " + origem);

        try (Socket socket = cliente;
             DataInputStream entrada = new DataInputStream(
                     new java.io.BufferedInputStream(socket.getInputStream()));
             DataOutputStream resposta = new DataOutputStream(socket.getOutputStream())) {

            int recebidas = 0;

            while (true) {
                String nomeArquivo;
                try {
                    // Quando o cliente fecha a conexao, readUTF lanca EOF e o
                    // laco termina normalmente.
                    nomeArquivo = entrada.readUTF();
                } catch (java.io.EOFException fim) {
                    break;
                }

                long tamanho = entrada.readLong();
                Path destino = caminhoSeguro(nomeArquivo);

                System.out.println("[SERVIDOR] Recebendo '" + nomeArquivo
                        + "' (" + tamanho + " bytes)...");

                // Le exatamente 'tamanho' bytes e grava direto no arquivo, em
                // blocos de 8 KB, sem carregar a imagem toda na memoria.
                try (OutputStream arquivo = new java.io.BufferedOutputStream(
                        new FileOutputStream(destino.toFile()))) {

                    byte[] buffer = new byte[8192];
                    long restante = tamanho;

                    while (restante > 0) {
                        int aLer = (int) Math.min(buffer.length, restante);
                        int lidos = entrada.read(buffer, 0, aLer);
                        if (lidos == -1) {
                            throw new IOException("conexao interrompida: faltaram "
                                    + restante + " bytes");
                        }
                        arquivo.write(buffer, 0, lidos);
                        restante -= lidos;
                    }
                }

                recebidas++;
                System.out.println("[SERVIDOR] Salvo em " + destino.toAbsolutePath());

                // Confirma o recebimento para o cliente.
                resposta.writeUTF("OK: " + destino.getFileName() + " (" + tamanho + " bytes)");
                resposta.flush();
            }

            System.out.println("[SERVIDOR] Cliente " + origem + " desconectou. Imagens recebidas: "
                    + recebidas + "\n");
        } catch (IOException e) {
            System.err.println("[SERVIDOR] Erro com o cliente " + origem + ": " + e.getMessage());
        }
    }

    /**
     * Monta o caminho de gravacao usando apenas o nome do arquivo, descartando
     * qualquer diretorio enviado pelo cliente. Isso impede que um nome como
     * "../../senha.txt" escreva fora da pasta de destino. Se o nome ja existir,
     * acrescenta um sufixo numerico em vez de sobrescrever.
     */
    private static Path caminhoSeguro(String nomeRecebido) {
        Path apenasNome = Paths.get(nomeRecebido).getFileName();
        String nome = (apenasNome == null || apenasNome.toString().isBlank())
                ? "imagem.bin"
                : apenasNome.toString();

        Path candidato = PASTA_DESTINO.resolve(nome);
        if (!Files.exists(candidato)) {
            return candidato;
        }

        // Separa "foto" de ".png" para numerar antes da extensao.
        int ponto = nome.lastIndexOf('.');
        String base = ponto > 0 ? nome.substring(0, ponto) : nome;
        String extensao = ponto > 0 ? nome.substring(ponto) : "";

        int contador = 1;
        while (Files.exists(candidato)) {
            candidato = PASTA_DESTINO.resolve(base + "_" + contador + extensao);
            contador++;
        }
        return candidato;
    }
}
