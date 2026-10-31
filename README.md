# Exercícios de Sockets TCP — Redes de Computadores (Fatec)

Quatro programas em **Java puro** (apenas `java.net` e `java.io`, sem bibliotecas
externas e sem `HttpClient`/`URLConnection`) demonstrando comunicação via sockets TCP.

| # | Exercício | Porta | Arquivos |
|---|-----------|-------|----------|
| 1 | Servidor e cliente trocando mensagens de texto | 9001 | [`ex1-chat-tcp/`](ex1-chat-tcp) |
| 2 | Servidor intermediando a conversa entre 2+ clientes | 9002 | [`ex2-chat-multiclientes/`](ex2-chat-multiclientes) |
| 3 | Servidor que recebe e guarda imagens binárias | 9003 | [`ex3-envio-imagens/`](ex3-envio-imagens) |
| 4 | Cliente que solicita a página inicial de `http://www.aied.com.br` | 80 | [`ex4-cliente-http/`](ex4-cliente-http) |

Requisito: **JDK 17 ou superior** (testado com OpenJDK 27).

---

## Exercício 1 — Troca de mensagens de texto (porta 9001)

Servidor e cliente conversam nos dois sentidos. Em cada programa, uma *thread*
dedicada fica lendo o que chega da rede enquanto a *thread* principal lê o
teclado e envia — sem isso o programa travaria esperando a digitação e não
exibiria as mensagens recebidas nesse meio tempo.

```bash
cd ex1-chat-tcp
javac *.java

# Terminal 1
java ServidorTCP

# Terminal 2
java ClienteTCP                      # ou: java ClienteTCP 192.168.0.10 9001
```

Digite `sair` em qualquer um dos lados para encerrar a conversa.

## Exercício 2 — Chat entre vários clientes (porta 9002)

O servidor não conversa: ele **intermedia**. Cada cliente aceito ganha sua
própria *thread* (`Atendente`) e toda mensagem recebida é repassada (*broadcast*)
para os demais participantes. A lista de clientes é um `Set` sincronizado,
porque várias *threads* a acessam ao mesmo tempo.

```bash
cd ex2-chat-multiclientes
javac *.java

# Terminal 1
java ServidorChat

# Terminais 2, 3, 4... (quantos quiser)
java ClienteChat
```

A primeira linha digitada no cliente é o **apelido**. O servidor avisa a sala
quando alguém entra ou sai e mostra quantos estão online.

## Exercício 3 — Envio de imagens binárias (porta 9003)

O cliente envia arquivos de imagem e o servidor os grava na pasta
`imagens_recebidas/`.

**Protocolo definido sobre o TCP**, repetido para cada imagem:

| Ordem | Campo | Método |
|-------|-------|--------|
| 1 | nome do arquivo | `writeUTF` |
| 2 | tamanho em bytes | `writeLong` |
| 3 | conteúdo binário | `write` |

Enviar o tamanho **antes** dos bytes é essencial: o TCP é um fluxo contínuo, sem
fronteira entre "mensagens", então o servidor precisa saber exatamente quantos
bytes ler para não misturar uma imagem com a próxima. A transferência é feita em
blocos de 8 KB, sem carregar o arquivo inteiro na memória.

```bash
cd ex3-envio-imagens
javac *.java

# Terminal 1
java ServidorImagens

# Terminal 2
java ClienteImagens foto.png
java ClienteImagens foto1.png foto2.jpg          # várias de uma vez
java ClienteImagens --host 192.168.0.10 foto.png # para outra máquina
```

Cuidados implementados no servidor:

- **Nomes repetidos** não sobrescrevem: `foto.png` chegando duas vezes gera
  `foto.png` e `foto_1.png`.
- **Caminhos maliciosos** são descartados: só o nome do arquivo é aproveitado,
  então um nome como `../../senha.txt` não escreve fora da pasta de destino.
- Cada imagem recebida é **confirmada** de volta para o cliente.

### Verificação de integridade

O teste abaixo compara o hash MD5 do original com o do arquivo recebido:

```bash
$ md5sum foto1.png
926f88e70769b94b3f3a1845122b31a4  foto1.png
$ md5sum imagens_recebidas/foto1.png
926f88e70769b94b3f3a1845122b31a4  imagens_recebidas/foto1.png
```

Também testado com um arquivo de **5 MB** (força centenas de leituras de 8 KB),
com hash idêntico ao final.

## Exercício 4 — Cliente HTTP sobre socket TCP puro

Mostra que o **HTTP é apenas texto trafegando sobre o TCP**: o programa abre o
socket na porta 80 e escreve a requisição na mão, linha por linha, no formato da
RFC 7230 — nenhuma biblioteca HTTP é usada.

```
GET / HTTP/1.1\r\n
Host: www.aied.com.br\r\n
User-Agent: ClienteTCP-Fatec/1.0\r\n
Accept: text/html\r\n
Connection: close\r\n
\r\n                        <- linha vazia marca o fim dos cabeçalhos
```

```bash
cd ex4-cliente-http
javac ClienteHTTP.java
java ClienteHTTP                        # http://www.aied.com.br
java ClienteHTTP http://example.com/    # outra URL, opcional
```

Pontos que o exercício evidencia:

- O terminador de linha do HTTP é **CRLF** (`\r\n`), não `\n`.
- O cabeçalho **`Host`** é obrigatório no HTTP/1.1, pois um mesmo IP pode
  hospedar vários sites (*virtual hosting*).
- **`Connection: close`** faz o servidor fechar a conexão ao terminar, então
  sabemos que a resposta acabou quando `read()` retorna `-1`.
- A resposta é lida como **bytes** (não texto), pois o corpo pode vir em
  qualquer codificação.
- A **linha em branco** (`\r\n\r\n`) separa os cabeçalhos do corpo.

O corpo recebido é salvo em `pagina-recebida.html`.

### Resultado obtido com `www.aied.com.br`

O site responde à requisição na porta 80 com um redirecionamento permanente
para HTTPS:

```
HTTP/1.1 301 Moved Permanently
location: https://www.aied.com.br/
Server: hcdn
```

O cliente então segue o cabeçalho `Location`. Para o endereço `https` ele usa
`SSLSocket`, que é um socket TCP comum com uma camada TLS por cima — a
requisição continua sendo escrita a mão, exatamente igual. Nesse segundo salto o
CDN que hospeda o site (Hostinger `hcdn`) responde:

```
HTTP/1.1 403 Forbidden
```

Esse `403` é **política do servidor**, que recusa requisições fora de um
navegador, e não um defeito do cliente: a requisição HTTP foi montada e
interpretada corretamente pelo servidor, como comprova a linha de status
recebida em cada salto. Para demonstrar o cliente obtendo uma página completa,
basta apontá-lo para um site que aceite a requisição:

```bash
$ java ClienteHTTP http://example.com/
[CLIENTE] DNS: example.com -> 104.20.23.154
[CLIENTE] Conectado em example.com:80
HTTP/1.1 200 OK
[CLIENTE] Pagina inicial salva em .../pagina-recebida.html (589 caracteres)
```

---

## Testes realizados

Todos os programas foram compilados com `javac -Xlint:all` **sem nenhum aviso**
e executados de verdade:

- **Ex. 1** — troca de mensagens confirmada nos dois sentidos.
- **Ex. 2** — três clientes simultâneos (Ana, Bruno, Carla); *broadcast*, avisos
  de entrada/saída e contagem de online corretos.
- **Ex. 3** — 5 imagens transferidas, incluindo uma de 5 MB; **hashes MD5
  idênticos** aos originais e renomeação de nomes duplicados funcionando.
- **Ex. 4** — requisição real a `www.aied.com.br` (301 → 403 do CDN) e a
  `example.com` (**200 OK** com HTML salvo).

## Observações sobre portas

Se alguma porta estiver ocupada, o erro será `Address already in use`. Para
descobrir quem a está usando:

```bash
ss -tlnp | grep 900
```
