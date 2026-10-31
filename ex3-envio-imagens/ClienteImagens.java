import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Exercicio 3 - Cliente que envia imagens binarias para o servidor (porta 9003).
 *
 * Segue o mesmo protocolo do ServidorImagens:
 *   nome do arquivo (UTF) + tamanho (long) + bytes da imagem.
 *
 * Execucao:
 *   javac ClienteImagens.java
 *   java ClienteImagens foto.png                      (envia para localhost)
 *   java ClienteImagens foto1.png foto2.jpg           (envia varias)
 *   java ClienteImagens --host 192.168.0.10 foto.png  (envia para outra maquina)
 */
public class ClienteImagens {

    private static final String HOST_PADRAO = "localhost";
    private static final int PORTA_PADRAO = 9003;

    public static void main(String[] args) {
        if (args.length == 0) {
            System.out.println("Uso: java ClienteImagens [--host HOST] [--porta PORTA] imagem1 [imagem2 ...]");
            return;
        }

        String host = HOST_PADRAO;
        int porta = PORTA_PADRAO;
        var arquivos = new java.util.ArrayList<Path>();

        // Leitura simples dos argumentos: flags opcionais e a lista de imagens.
        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--host" -> host = args[++i];
                case "--porta" -> porta = Integer.parseInt(args[++i]);
                default -> arquivos.add(Paths.get(args[i]));
            }
        }

        // Valida os arquivos antes de abrir a conexao.
        for (Path arquivo : arquivos) {
            if (!Files.isRegularFile(arquivo)) {
                System.err.println("[CLIENTE] Arquivo nao encontrado: " + arquivo);
                return;
            }
        }
        if (arquivos.isEmpty()) {
            System.err.println("[CLIENTE] Nenhuma imagem informada.");
            return;
        }

        System.out.println("[CLIENTE] Conectando em " + host + ":" + porta + "...");

        try (Socket socket = new Socket(host, porta);
             DataOutputStream saida = new DataOutputStream(
                     new java.io.BufferedOutputStream(socket.getOutputStream()));
             DataInputStream confirmacao = new DataInputStream(socket.getInputStream())) {

            System.out.println("[CLIENTE] Conectado.\n");

            for (Path arquivo : arquivos) {
                long tamanho = Files.size(arquivo);
                String nome = arquivo.getFileName().toString();

                System.out.println("[CLIENTE] Enviando '" + nome + "' (" + tamanho + " bytes)...");

                // 1) nome, 2) tamanho, 3) bytes - na ordem que o servidor espera.
                saida.writeUTF(nome);
                saida.writeLong(tamanho);

                // Copia o arquivo direto para o socket em blocos de 8 KB.
                try (InputStream imagem = new java.io.BufferedInputStream(
                        Files.newInputStream(arquivo))) {

                    byte[] buffer = new byte[8192];
                    int lidos;
                    while ((lidos = imagem.read(buffer)) != -1) {
                        saida.write(buffer, 0, lidos);
                    }
                }

                // flush() forca o envio do que ainda esta no buffer, senao o
                // servidor pode ficar esperando bytes que nunca sairam.
                saida.flush();

                // Espera o servidor confirmar que gravou a imagem.
                System.out.println("[SERVIDOR] " + confirmacao.readUTF() + "\n");
            }

            System.out.println("[CLIENTE] Todas as imagens foram enviadas.");
        } catch (java.net.ConnectException e) {
            System.err.println("[CLIENTE] Nao foi possivel conectar. O servidor esta rodando?");
        } catch (IOException e) {
            System.err.println("[CLIENTE] Erro: " + e.getMessage());
        }
    }
}
