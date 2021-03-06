/*
 * @Author Triston Gregoire
 */

import java.io.*;
import java.net.*;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class SuperPeerThread extends Thread {
    CopyOnWriteArrayList<PeerCB> peers;
    CopyOnWriteArrayList<SuperPeerCB> neighbors;

    Socket sock;
    PrintWriter writer;
    BufferedReader reader;

    MainFrame frame;


    public SuperPeerThread(CopyOnWriteArrayList peerTable, CopyOnWriteArrayList superPeerTable, Socket sock, MainFrame frame) throws IOException {
        this.peers = peerTable;
        this.neighbors = superPeerTable;
        this.sock = sock;
        this.writer = new PrintWriter(sock.getOutputStream(), true);
        this.reader = new BufferedReader(new InputStreamReader(sock.getInputStream()));
        this.frame = frame;
    }

    @Override
    public void run() {
        try {
            ServiceType type = categorize();
            service(type);

            sock.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public ServiceType categorize() throws IOException {
        String inputString;
        inputString = reader.readLine();
        ServiceType type = ServiceType.valueOf(inputString);
        return type;
    }

    public void service(ServiceType type) throws IOException {
        switch (type){
            case QUERY: handleQuery();
                break;
            case REQUEST: handleRequest();
                break;
            case RESPONSE: handleResponse();
                break;
            case REGISTER: handleRegistration(ServiceType.REGISTER);
            break;
            case REGISTER_SUPER: handleRegistration(ServiceType.REGISTER_SUPER);
            default: System.out.println("Invalid communication type!");
        }
    }
    public void handleQuery() throws IOException {
        System.out.println("HANDLING QUERY");
       String input;
       //assert input == ServiceType.QUERY.getValue();
       while ((input = reader.readLine()) != null){
           for (PeerCB peer : peers) {
               List<FileCB> resources = peer.getResource();
               for (FileCB resource : resources) {
                   if (input.equalsIgnoreCase(resource.fileName)){
                       System.out.printf("RESOURCE: %s FOUND. SENDING RESPONSE%n", input);
                       writer.println(ServiceType.RESPONSE.getValue());
                       writer.println(peer.getIP());
                       writer.println(peer.getPort());
                       writer.println(resource.getFileSize());
                       return;
                   }
               }
           }
       }
       System.out.println("COULD NOT FIND RESOURCE");
       writer.println(ServiceType.RESPONSE.getValue());
       writer.println("N-A");
    }
    public void handleResponse() throws IOException {
        System.out.println("HANDLING RESPONSE");
        List <String> resultSet = new ArrayList<String>();
        String temp;
        while ((temp = reader.readLine()) != null){
            resultSet.add(temp);
        }
        assert resultSet.size() == 2 : "Result set unexpectedly large at size: " + resultSet.size();
        for (String str : resultSet) {
            writer.println();
        }


    }
    public void handleRequest() throws IOException {
        String input;
        while ((input = reader.readLine()) != null) {
            System.out.printf("HANDLING REQUEST FOR: %s%n", input);
            for (PeerCB peer : peers) {
                List<FileCB> resourceList = peer.getResource();
                for (FileCB file : resourceList) {
                    if (file.getFileName().equalsIgnoreCase(input)){
                        writer.println(ServiceType.RESPONSE.getValue());
                        writer.println(peer.getIP());
                        writer.println(peer.getPort());
                        writer.println(file.getFileSize());
                        writer.println(ServiceType.END.getValue());
                        return;
                    }
                }
            }
            List<String> remoteResult = queryNeighbors(input);
            if (remoteResult != null) {
                writer.println(remoteResult.get(0));
                writer.println(remoteResult.get(1));
                writer.println(remoteResult.get(2));
                writer.println(remoteResult.get(3));
                writer.println(ServiceType.END.getValue());
            }
            else{writer.println(ServiceType.NA.getValue());}
        }
    }

    public void handleRegistration(ServiceType type) throws IOException {
        System.out.println("HANDLING REGISTRATION");
        if (ServiceType.REGISTER == type){
            String str;
            FileCB fileCB = new FileCB();
            List<String> inputList = new ArrayList<>();
            while ((str = reader.readLine()) != null) {
                inputList.add(str);
                if (ServiceType.END.getValue().equals(str)){
                    break;
                }
            }


            //String ip = inputList.get(0);
            //String resource = inputList.get(0);
            String port = inputList.get(0);
            inputList.remove(0);
            inputList.remove(inputList.size() - 1);
            PeerCB newPeer = new PeerCB(sock, inputList, port);

            int i = 0;
            for (PeerCB peer : peers) {
                if (peer.getIP().equals(newPeer.getIP()) && peer.getPort() == newPeer.getPort()){
                    //peer = newPeer;
                    peers.set(i, newPeer);
                    return;
                }
            }
            peers.add(newPeer);
            frame.addNode(newPeer);
        }
        else if(ServiceType.REGISTER_SUPER == type){
            String str;
            List<String > inputList = new ArrayList<>();
            while ((str = reader.readLine()) != null){
                inputList.add(str);
            }
            String ip = inputList.get(0);
            String port = inputList.get(1);
            SuperPeerCB superPeerCB = new SuperPeerCB(ip, port);
            neighbors.add(superPeerCB);
        }
    }

    private List<String> queryNeighbors(String input) throws IOException {
        System.out.println("SENDING QUERY TO NEIGHBORING SUPER PEERS");
        for (SuperPeerCB neighbor : neighbors) {
            if(!Utility.checkIPv4( neighbor.getIP() )){
                return null;
            }
            Socket querySocket = new Socket();
            try {
                querySocket.connect(new InetSocketAddress(neighbor.getIP(), neighbor.getPort()), Utility.THIRTY_SECONDS);
            }catch (ConnectException | SocketTimeoutException e){
                e.printStackTrace();
                System.out.println("Super peer at "+ neighbor.getIP() +" is unavailable");
                continue;
            }
            PrintWriter out = new PrintWriter(querySocket.getOutputStream(), true);
            BufferedReader in = new BufferedReader(new InputStreamReader(querySocket.getInputStream()));
            out.println(ServiceType.QUERY.getValue());
            out.println(input);

            String temp;
            List<String> responseList = new ArrayList<>();
            while((temp = in.readLine()) != null){
                responseList.add(temp);
            }
            System.out.println("RESPONSE FROM QUERY RECEIVED");
            if (ServiceType.RESPONSE.getValue().equals(responseList.get(0))){
                if (Utility.checkIPv4(responseList.get(1))){
                    return responseList;
                }
                else if (ServiceType.NA.getValue().equals(responseList.get(1))){
                    System.out.println("Neighboring super peer at address " + neighbor.getIP() + " couldn't find resource " + input);
                }
                else{
                    System.out.println("EXPECTED IPv4 OR N-A ENUM BUT FOUND INSTEAD: " + responseList.get(1));
                }
            }
        }
        return null;
    }
}
