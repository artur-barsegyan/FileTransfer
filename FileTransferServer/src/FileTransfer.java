import java.io.*;
import java.net.*;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

import static java.nio.file.StandardOpenOption.WRITE;
import static java.nio.file.StandardOpenOption.CREATE;

public class FileTransfer {
    private static Path downloadDir = null;

    private static int HEADER_LENGTH_SIZE = 4;
    private static int FILE_LENGTH_SIZE = 8;

    public static final int BUFFER_SIZE = 1024 * 1024 * 8;

    static Path createDownloadDirectory() throws IOException {
        Path downloadDir = Paths.get(new File(".").getCanonicalPath() + "/uploads");
        if (!Files.exists(downloadDir))
            Files.createDirectory(downloadDir);

        return downloadDir;
    }

    public static void main(String[] args) {
        if (args.length < 2 || !args[0].equals("-p")) {
            System.out.println("Error! Enter port number for input connections");
            return;
        }

        try {
            int refreshTimeout = 2000;

            ServerSocketChannel server = ServerSocketChannel.open();
            int portNumber = Integer.parseInt(args[1]);
            SocketAddress port = new InetSocketAddress(portNumber);
            server.socket().bind(port);
            server.configureBlocking(true);

            Selector selector = Selector.open();
            int operations = SelectionKey.OP_READ | SelectionKey.OP_WRITE;
            ByteBuffer buf = ByteBuffer.allocate(BUFFER_SIZE);
            downloadDir = createDownloadDirectory();

            for (; ; ) {
                SocketChannel newClient = server.accept();

                if (newClient != null) {
                    server.configureBlocking(false);
                    newClient.configureBlocking(false);
                    System.out.println("New client: " + newClient.socket().getInetAddress());
                    SelectionKey newChannel = newClient.register(selector, operations);
                    newChannel.attach(new ByteArrayOutputStream());
                } else if (selector.keys().size() > 0) {
                    selector.select(refreshTimeout);
                    Set<SelectionKey> ready = selector.selectedKeys();
                    Iterator<SelectionKey> readyChannelsIter = ready.iterator();

                    while (readyChannelsIter.hasNext()) {
                        SelectionKey key = readyChannelsIter.next();

                        if (key.isReadable()) {
                            SocketChannel currentSocket = (SocketChannel) key.channel();
                            Object fileData = key.attachment();

                            long readyBytes = currentSocket.read(buf);

                            if (readyBytes < 0) {
                                if (fileData != null) {
                                    if (fileData instanceof SeekableByteChannel) {
                                        System.out.println("[DONE]");
                                        ((SeekableByteChannel) fileData).close();

                                    } else
                                        ((ByteArrayOutputStream) fileData).close();
                                }

                                key.cancel();
                                continue;
                            }

                            // IMPORTANT: Attach stream to write in file when we get a header of this file!
                            // End of downloading => Close file and close connection
                            if (fileData != null && fileData instanceof SeekableByteChannel) {
                                ((SeekableByteChannel) fileData).write(buf);
                                buf.clear();
                            } else if (fileData != null && fileData instanceof ByteArrayOutputStream) {
                                ((ByteArrayOutputStream) fileData).write(buf.array(), 0, buf.position());
                                byte[] data = ((ByteArrayOutputStream) fileData).toByteArray();
                                FileHeader header = parseHeader(data);

                                if (header != null) {
                                    key.attach(Files.newByteChannel(Paths.get(downloadDir.toAbsolutePath() + "/" + header.fileName),
                                            EnumSet.of(WRITE, CREATE)));

                                    if (data.length - header.headerSize > 0) {
                                        ByteBuffer remainder = ByteBuffer.wrap(data, header.headerSize, data.length - header.headerSize);
                                        ((SeekableByteChannel) key.attachment()).write(remainder);
                                    }

                                    System.out.println("Download \"" + header.fileName + "\" [" + header.fileSize  + " bytes]");
                                }
                            }

                            buf.clear();
                        }

                        if (!key.isValid()) {
                            System.out.println("Remove client: " + ((SocketChannel) key.channel()).socket().getInetAddress());
                            key.cancel();
                        }

                        readyChannelsIter.remove();
                    }
                } else
                    server.configureBlocking(true);

            }
        } catch (NumberFormatException err) {
            System.out.println("Argument doesn't contain a port number!");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static FileHeader parseHeader(byte[] bytes) {
        int headerLength;
        long fileSize;
        String fileName = "";
        ByteBuffer parser = ByteBuffer.wrap(bytes);

        if (bytes.length >= 4)
            headerLength = parser.getInt(0);
        else
            return null;

        if (bytes.length >= headerLength) {
            fileSize = parser.getLong(4);
            byte[] fileNameArray = new byte[headerLength - FILE_LENGTH_SIZE];
            System.arraycopy(bytes, HEADER_LENGTH_SIZE + FILE_LENGTH_SIZE, fileNameArray, 0, headerLength - FILE_LENGTH_SIZE);

            try {
                fileName = new String(fileNameArray, "UTF-8");
            } catch (UnsupportedEncodingException e) {
                e.printStackTrace();
            }

            FileHeader header = new FileHeader();
            /* Cross platform: File (constructor).getName - remove '/' and '\' */
            header.fileName = new File(fileName).getName();
            header.headerSize = headerLength + HEADER_LENGTH_SIZE;
            header.fileSize = fileSize;

            return header;
        } else
            return null;
    }
}
