package ru;

import java.io.*;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;


public class Main {
    private static long Size = 0;
    private static byte[] Data = null;
    private static int SizeOfHead = 0;
    private static byte[] Name = null;

    public static void main(String[] args) throws UnsupportedEncodingException {

        if(args.length < 3)
        {
            System.out.println("Check your filename, ip and port");
            return;
        }

        File CP = new File(args[0]);
        String filename = CP.getName();
        Name = filename.getBytes();
        String fileNameUTF = new String(Name, "UTF-8");

        try(
                SocketChannel channel = SocketChannel.open();
                RandomAccessFile file = new RandomAccessFile(args[0], "r")
        )

        {
            Size = file.length();
            Data = new byte[(int)file.length()];
            file.readFully(Data);
            channel.connect(new InetSocketAddress(args[1], Integer.parseInt(args[2])));
            System.out.println("Success connecting");
            System.out.println("Sending data...");
            ByteBuffer bb = ByteBuffer.allocate(4 + 8 + fileNameUTF.getBytes().length + Data.length);
            SizeOfHead = fileNameUTF.getBytes().length + 8;
            bb.putInt(SizeOfHead);
            bb.putLong(Size);
            bb.put(fileNameUTF.getBytes());
            bb.put(Data);
            bb.position(0); //flip
            channel.write(bb);
        }


        catch(IOException ex)
        {
            System.out.println(ex.getMessage());
        }
    }
}
