/*
 * @Author Triston Gregoire
 */

import javafx.concurrent.Task;

import javax.swing.*;
import javax.swing.SwingWorker;
import java.awt.*;
import java.io.*;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

public class Client extends TimeKeeper{
    public Socket getSock() {
        return sock;
    }

    public void setSock(Socket sock) {
        this.sock = sock;
    }

    private Socket sock;
    private byte[] buffer = new byte[8192];
    Client(String ip, int port) throws IOException {
        System.out.println(ip);
        System.out.println(port);
        this.sock = new Socket(ip, port);

    }
    public void request(String resource) throws IOException {
        BufferedReader reader = new BufferedReader(new InputStreamReader(sock.getInputStream()));
        OutputStream outputStream = sock.getOutputStream();
        PrintWriter out = new PrintWriter(outputStream, true);
        out.println(ServiceType.REQUEST.getValue());
        out.println(resource);

        String response;
        List<String> responseList= new ArrayList<>();
        while ((response = reader.readLine()) != null){
            responseList.add(response);
            if (response.equals(ServiceType.END.getValue())){
                break;
            } else if (response.equals(ServiceType.NA.getValue())) {
                JOptionPane.showMessageDialog(null,resource + " is not available locally or remotely!");
                return;
            }
        }

        for (String str : responseList) {
            System.out.println(str);
        }
        String ip = responseList.get(1);
        int port = Integer.parseInt(responseList.get(2));
        int size = Integer.parseInt(responseList.get(3));
        transfer(ip, port, resource, size);
        //OptionPane.showMessageDialog(null, "Transfer complete!");
        Desktop.getDesktop().open(new File(resource));

        sock.close();
    }
    private void transfer(String ip, int port, String resource, int size) throws IOException {
        startTime = System.nanoTime();
        Socket peerSocket = new Socket(ip, port);
        InputStream inputStream = peerSocket.getInputStream();
        BufferedReader reader = new BufferedReader(new InputStreamReader(peerSocket.getInputStream()));
        PrintWriter writer = new PrintWriter(peerSocket.getOutputStream(),true);

        writer.println(ServiceType.RESOURCE.getValue());
        writer.println(resource);
        writer.println(ServiceType.END.getValue());

        File video = new File(resource);
        FileOutputStream fileOutputStream = new FileOutputStream(video);


        int currentProgress = 0;
        int count;
        int j = 0;

        while ((count = inputStream.read(buffer)) > 0){
            fileOutputStream.write(buffer, 0, count);
            currentProgress = currentProgress + count;
            if (Utility.getPercentage((j % 5), size) == 0 || Utility.getPercentage(currentProgress,size) == 100.0){
                System.out.println("Download Progress ... " + Utility.getPercentage(currentProgress, size) + "%");
            }
            j++;
        }
        endTime = System.nanoTime();

        double transferSize = (video.length() / 1000000.0);
        double transferTime = (endTime - startTime) / FROMNANO;
        double transferRate = transferSize / transferTime;

        transferSize = Utility.round(transferSize, Utility.SIGFIGS);
        transferTime = Utility.round(transferTime, Utility.SIGFIGS);
        transferRate = Utility.round(transferRate, Utility.SIGFIGS);

        System.out.println(transferTime + " seconds");
        System.out.println(transferRate + " MB/s");
        Object[] metrics = {transferTime, transferRate, transferSize};
        fileOutputStream.close();
        //Utility.initializeOutput();
        Utility.appendToFile(metrics);
        sock.close();
    }
}
