
import java.io.*;
import java.net.*;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class DatabaseNode {
    static Map<Integer, String> connections = new ConcurrentHashMap<>();
    static ServerSocket server = null;
    static PrintWriter nodeCom;
    static BufferedReader nodeResp;
    static Socket client = null;
    private static DatagramSocket UDpserver;
    static byte[] buff = new byte[576 - 60 - 8];
    static int key;
    static int value;
    static int UDPport;
    static String UdpAddress;
    static Map<Integer, String> askers = new ConcurrentHashMap<>();
    static int identifier = 0;

    public static void createProcess(int tcpPort) { //setting up the parallel threads of UTP and TCP
        try {
            String[] addr = String.valueOf(InetAddress.getLocalHost()).split("/");
            UdpAddress = addr[1];
            UDPport = tcpPort;
            UDpserver = new DatagramSocket(UDPport);
            server = new ServerSocket(tcpPort);
            if (!connections.isEmpty()) {
                for (Integer port : connections.keySet()) {
                    System.out.println("Connecting to " + connections.get(port) + ":" + port);
                    Socket socket = new Socket(connections.get(port), port);
                    nodeCom = new PrintWriter(socket.getOutputStream(), true);
                    nodeCom.println("Hello");
                    nodeCom.println("Connect:" + UdpAddress + ":" + UDPport);
                    nodeCom.println();
                    nodeResp = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                    String newAdress = null;
                    String line = nodeResp.readLine();

                    //letting the network name itself
                    if (line != null && !line.isEmpty())
                        newAdress = nodeResp.readLine();
                    if (newAdress != null) {
                        connections.replace(port, newAdress);
                        System.out.println("New address for port "+ port + "is " + newAdress);
                    }
                    socket.close();
                }
            }
        } catch (IOException e) {
            System.out.println("Problems with connection");
            System.exit(-1);
        }
        //the udp/tcp
        (new UDPThread()).start();
        System.out.println("Server listens on port: " + server.getLocalPort());

        while (true) {
            try {
                client = server.accept();
            } catch (IOException e) {
                System.out.println("Couldn't accept this client");
                System.exit(-1);
            }
            try {
                //the tcp
                (new ServerThread(client, server, connections)).start();
            } catch (SocketException e) {
                System.out.println("Something is wrong with the tcp connection");
            }
        }
    }
    static class UDPThread extends Thread{ //settings of UDP/TCP bt Nodes for queries
        public UDPThread(){
            super();
        }

        //function for max and min functions
        public void TCPminMAX(InetAddress clientAddress, int clientPort, int ident, String res, String quant){
            Socket tempSocket;
            try {
                tempSocket = new Socket(clientAddress, clientPort);
                PrintWriter out = new PrintWriter(tempSocket.getOutputStream(), true);
                out.println(quant);
                out.println(ident);
                out.println(res);
                out.println();
                tempSocket.close();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
        //function for solutions
        public void  TcpResp(InetAddress clientAddress, int clientPort, int ident, String res){
            Socket tempSocket;
            try {
                tempSocket = new Socket(clientAddress, clientPort);
                PrintWriter out = new PrintWriter(tempSocket.getOutputStream(), true);
                out.println(ident);
                out.println(res);
                out.println();
                tempSocket.close();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
        @Override
        public void run() {
            //queries
            while (true) {
                DatagramPacket datagram = new DatagramPacket(buff, buff.length);
                try {
                    UDpserver.receive(datagram);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
                System.out.println("Queries Thread running");
                int clientPort = 0;
                InetAddress clientAddress = null;
                StringBuilder attach = null;
                StringBuilder attachBack = null;
                String querry = new String(datagram.getData(), 0, datagram.getLength()).trim();
                String[] type = querry.split(" ");

                //figuring out who to send to if it's time to send the response
                if (!(type.length <= 1)) {
                    System.out.println(querry);
                    attach = new StringBuilder(type[2]);
                    if (type[2].contains("-")) {
                        String[] addresses = type[2].split("-");
                        attachBack = new StringBuilder();
                        for (int i = 0; i < addresses.length - 1; i++) {
                            attachBack.append(addresses[i]);
                            if (i < addresses.length - 2)
                                attachBack.append("-");
                        }
                        String[] client = addresses[addresses.length - 1].split(":");
                        clientPort = Integer.parseInt(client[0]);
                        try {
                            clientAddress = InetAddress.getByName(client[1]);

                        } catch (UnknownHostException e) {
                            throw new RuntimeException(e);
                        }
                    } else {
                        String[] client = type[2].split(":");
                        clientPort = Integer.parseInt(client[0]);
                        try {
                            clientAddress = InetAddress.getByName(client[1]);
                        } catch (UnknownHostException e) {
                            throw new RuntimeException(e);
                        }
                    }
                }
                System.out.println("Recieved request from " + datagram.getPort());
                switch (type[0]) {
                    case "SET": {
                        String[] parameters = type[1].split(":");
                        //if this node has it
                        if (key == Integer.parseInt(parameters[0])) {
                            value = Integer.parseInt(parameters[1]);
                            System.out.println("I changed it");
                            //if next address is the requesting node
                            if (!type[2].contains("-")) {
                                System.out.println("I'm sending it");
                                TcpResp(clientAddress, clientPort, Integer.parseInt(type[3]), "OK");
                            } else {
                                //if theres >= 1 before the requesting node
                                System.out.println("I send it backwards");
                                byte[] respBuff = ("DONE" + " " + "OK" + " " + attachBack + " " + type[3]).getBytes();
                                DatagramPacket traverse = new DatagramPacket(respBuff, respBuff.length,
                                        clientAddress, clientPort);
                                try {
                                    UDpserver.send(traverse);
                                } catch (IOException e) {
                                    throw new RuntimeException(e);
                                }
                            }
                        }
                        //sending query to the rest
                        else {
                            System.out.println("My connections: " + connections);
                            byte[] queryBuff = ("SET " + type[1] + " " + attach + "-" + UDPport + ":" + UdpAddress + " "
                                    + type[3] + "\r\n").getBytes();
                            int i = 0;
                            for (Integer node : connections.keySet()) {
                                System.out.println("iteration num " + i);
                                i++;
                                if (type[2].contains(node + ":" + connections.get(node))) {
                                    System.out.println("Already visited " + connections.get(node) + ":" + node);
                                    continue;
                                }
                                try {
                                    InetAddress address = InetAddress.getByName(connections.get(node));
                                    DatagramPacket query = new DatagramPacket(queryBuff, queryBuff.length,
                                            address, node);
                                    UDpserver.send(query);
                                } catch (Exception e) {
                                    e.printStackTrace();
                                    System.out.println("A problem to connect with other nodes");
                                }
                            }
                        }
                    }
                    break;
                    case "GET": {
                        //if this node has it
                           if (key == Integer.parseInt(type[1])) {
                               System.out.println("I have it");
                            //if next address is the requesting node
                            if (!type[2].contains("-")) {
                                System.out.println("I'm sending it ");
                                TcpResp(clientAddress, clientPort, Integer.parseInt(type[3]), key + ":" + value);
                            } else {
                                //if theres >= 1 before the requesting node
                                byte[] respBuff = ("DONE" + " " + key + ":" + value + " " +
                                        attachBack + " " + type[3]).getBytes();
                                DatagramPacket traverse = new DatagramPacket(respBuff, respBuff.length,
                                        clientAddress, clientPort);
                                try {
                                    UDpserver.send(traverse);
                                } catch (IOException e) {
                                    throw new RuntimeException(e);
                                }
                            }
                        }
                           //sending the query to the rest
                            else {
                               System.out.println("My connections: " + connections);
                            byte[] queryBuff = ("GET " + type[1] + " " + attach + "-" + UDPport + ":" + UdpAddress + " "
                                     + type[3] + "\r\n").getBytes();
                            int i = 0;
                            for (Integer node : connections.keySet()) {
                                System.out.println("iteration num " + i);
                                i++;
                                if (type[2].contains(node + ":" + connections.get(node))) {
                                    System.out.println("Already visited " + connections.get(node) + ":" + node);
                                    continue;
                                }
                                try {
                                    InetAddress address = InetAddress.getByName(connections.get(node));
                                    DatagramPacket query = new DatagramPacket(queryBuff, queryBuff.length,
                                            address, node);
                                    UDpserver.send(query);
                                } catch (Exception e) {
                                    e.printStackTrace();
                                    System.out.println("A problem to connect with other nodes");
                                }
                            }
                        }
                    }
                    break;
                    case "FIND": {
                        //if this node has it
                        if (key == Integer.parseInt(type[1])) {
                            System.out.println("I have it");
                            //if next address is the requesting node
                            if (!type[2].contains("-")) {
                                System.out.println("I'm sending it " + type[3]);
                                TcpResp(clientAddress, clientPort, Integer.parseInt(type[3]),
                                        UdpAddress + ":" + UDPport);
                            } else {
                                //if theres >= 1 before the requesting node
                                byte[] respBuff = ("DONE" + " " + UdpAddress + ":" +
                                        UDPport + " " + attachBack + " " + type[3]).getBytes();
                                DatagramPacket traverse = new DatagramPacket(respBuff, respBuff.length,
                                        clientAddress, clientPort);
                                try {
                                    UDpserver.send(traverse);
                                } catch (IOException e) {
                                    throw new RuntimeException(e);
                                }
                            }
                        }
                        //sending query to the rest
                        else {
                            System.out.println("My connections: " + connections);
                            byte[] queryBuff = ("FIND " + type[1] + " " + attach +
                                    "-" + UDPport + ":" + UdpAddress + " "
                                    + type[3] + "\r\n").getBytes();
                            int i = 0;
                            for (Integer node : connections.keySet()) {
                                System.out.println("iteration num " + i);
                                i++;
                                if (type[2].contains(node + ":" + connections.get(node))) {
                                    System.out.println("Already visited " + connections.get(node) + ":" + node);
                                    continue;
                                }
                                try {
                                    InetAddress address = InetAddress.getByName(connections.get(node));
                                    DatagramPacket query = new DatagramPacket(queryBuff, queryBuff.length,
                                            address, node);
                                    UDpserver.send(query);
                                } catch (Exception e) {
                                    e.printStackTrace();
                                    System.out.println("A problem to connect with other nodes");
                                }
                            }
                        }
                    }
                    break;
                    case "MAX": {
                        boolean TreeEnd = true;
                        String[] parameters = type[1].split(":");
                        if (value > Integer.parseInt(parameters[1]))
                            type[1] = key + ":" + value;
                        type[4] = type[4] + "-" + UdpAddress + "|" + UDPport;
                        byte[] queryBuff = ("MAX " + type[1] +
                                " " + attach + "-" + UDPport + ":" + UdpAddress + " " + type[3] + " " +
                                type[4] + "\r\n").getBytes();
                        for (Integer node : connections.keySet()) {
                            if (type[4].contains(connections.get(node) + "|" + node))
                                continue;
                            try {
                                TreeEnd = false;
                                InetAddress address = InetAddress.getByName(connections.get(node));
                                DatagramPacket query = new DatagramPacket(queryBuff, queryBuff.length,
                                        address, node);
                                UDpserver.send(query);
                            } catch (Exception e) {
                                System.out.println("A problem to connect with other nodes");
                            }
                        }
                        if(TreeEnd) {
                            if (!type[2].contains("-")) {
                                System.out.println("I'm sending it " + type[1] + " " + type[4]);
                                TCPminMAX(clientAddress, clientPort, Integer.parseInt(type[3]), type[1] +
                                        ":" + type[4], "MAX");
                            } else {
                                byte[] respBuff = ("OVER_MAX " + type[1] +
                                        " " + attachBack + " " + type[3] + " " + type[4] + "\r\n").getBytes();
                                DatagramPacket response1 = new DatagramPacket(respBuff, respBuff.length,
                                        clientAddress, clientPort);
                                try {
                                    UDpserver.send(response1);
                                } catch (IOException e) {
                                    throw new RuntimeException(e);
                                }
                            }
                        }
                    }
                    break;
                    case "MIN": {
                        boolean EndTree = true;
                        String[] parameters = type[1].split(":");
                        if (value < Integer.parseInt(parameters[1]))
                            type[1] = key + ":" + value;
                        type[4] = type[4] + "-" + UdpAddress + "|" + UDPport;
                        byte[] queryBuff = ("MIN " + type[1] +
                                " " + attach + "-" + UDPport + ":" + UdpAddress + " " + type[3] + " " + type[4] + "\r\n").getBytes();
                        for (Integer node : connections.keySet()) {
                            if (type[4].contains(connections.get(node) + "|" + node))
                                continue;
                            try {
                                EndTree = false;
                                InetAddress address = InetAddress.getByName(connections.get(node));
                                DatagramPacket query = new DatagramPacket(queryBuff, queryBuff.length,
                                        address, node);
                                UDpserver.send(query);
                            } catch (Exception e) {
                                System.out.println("A problem to connect with other nodes");
                            }
                        }
                        if(EndTree) {
                            if (!type[2].contains("-")) {
                                System.out.println("I'm sending it " + type[1] + " " + type[4]);
                                TCPminMAX(clientAddress, clientPort,
                                        Integer.parseInt(type[3]), type[1] + ":" + type[4], "MIN");
                            } else {
                                byte[] respBuff = ("OVER_MIN " + type[1] +
                                        " " + attachBack + " " + type[3] + " " + type[4] + "\r\n").getBytes();
                                DatagramPacket response1 = new DatagramPacket(respBuff, respBuff.length,
                                        clientAddress, clientPort);
                                try {
                                    UDpserver.send(response1);
                                } catch (IOException e) {
                                    throw new RuntimeException(e);
                                }
                            }
                        }
                    }
                    break;
                    case "DONE": {
                       if(!type[2].contains("-")){
                           TcpResp(clientAddress, clientPort, Integer.parseInt(type[3]), type[1]);
                       }
                       else{
                           byte[] respBuff = ("DONE" + " " + type[1] + " " + attachBack + " " + type[3]).getBytes();
                           DatagramPacket traverse = new DatagramPacket(respBuff, respBuff.length,
                                   clientAddress, clientPort);
                           try {
                               UDpserver.send(traverse);
                           } catch (IOException e) {
                               throw new RuntimeException(e);
                           }
                       }
                    }
                    break;
                    case "OVER_MAX":{
                        if(!type[2].contains("-")){
                            TCPminMAX(clientAddress, clientPort, Integer.parseInt(type[3]), type[1] +
                                    ":" + type[4], "MAX");
                        }
                        else{

                            byte[] respBuff = ("OVER_MAX" + " " + type[1] + " " + attachBack +
                                    " " + type[3] + " " + type[4]).getBytes();
                            DatagramPacket traverse = new DatagramPacket(respBuff, respBuff.length,
                                    clientAddress, clientPort);
                            try {
                                UDpserver.send(traverse);
                            } catch (IOException e) {
                                throw new RuntimeException(e);
                            }
                        }
                    }
                    break;
                    case "OVER_MIN": {
                        if (!type[2].contains("-")) {
                            TCPminMAX(clientAddress, clientPort, Integer.parseInt(type[3]),
                                    type[1] + ":" + type[4], "MIN");
                        } else {

                            byte[] respBuff = ("OVER_MIN" + " " + type[1] + " " +
                                    attachBack + " " + type[3] + " " + type[4]).getBytes();
                            DatagramPacket traverse = new DatagramPacket(respBuff, respBuff.length,
                                    clientAddress, clientPort);
                            try {
                                UDpserver.send(traverse);
                            } catch (IOException e) {
                                throw new RuntimeException(e);
                            }
                        }
                    }break;
                    default:
                        System.out.println("I don't understand");
                }
            }
        }
    }
    static class ServerThread extends Thread{
        Map<Integer, String> connections;

        private final Socket socket;

        ServerSocket server;
        int timeout = 5_000;

        public ServerThread(Socket socket, ServerSocket server, Map<Integer, String> connections) throws SocketException {
            super();
            this.socket = socket;
            this.server = server;
            this.connections = connections;

        }

        public void run() {
                System.out.println("Main Thread running");
                try {
                    //tcp connection for new nodes, departing nodes, clients
                    BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                    PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
                    String line;
                    String operation = null;
                    String parameter = null;

                    line = in.readLine();

                    //if a client wants something
                    if (line != null && (line.contains("get")||
                            line.contains("set")||line.contains("terminate")||
                            line.contains("new")||line.contains("find"))) {
                        String[] strr = line.split(" ");
                        operation = strr[0];
                        try {
                            parameter = strr[1];
                        }catch (Exception e){
                            //no need to do anything just a function without parameters
                        }
                        //if the node is joining
                    } else
                        if (line != null && line.contains("Hello")) {
                        while ((line = in.readLine()) != null && !line.isEmpty()) {
                            if (line.contains("Connect:")) {
                                String[] strr = line.split(":");
                                connections.put(Integer.parseInt(strr[2]),strr[1]);
                            }
                        }
                        out.println("That is my actual address: ");
                        out.println(UdpAddress);
                        out.println();
                        //if the node is leaving
                    } else
                        if (line != null && line.contains("Goodbye")) {
                        String[] strr;
                        while ((line = in.readLine()) != null && !line.isEmpty()) {
                            if (line.contains("Disconnect:")) {
                                strr = line.split(":");
                                connections.remove(Integer.parseInt(strr[1]),strr[2]);
                            }
                        }
                        //if there is a candidate for max
                    } else
                      if (line != null && line.contains("MAX")) {
                          System.out.println("Recieved a candidate");
                          int i = Integer.parseInt(in.readLine());
                          String a = in.readLine();
                          String[] arr = a.split(":");
                          String[] ready = askers.get(i).split(":");
                          if(Integer.parseInt(arr[1]) > Integer.parseInt(ready[1]))
                              askers.replace(i, a);
                    }
                      //if there is candidate for min
                      else
                      if (line != null && line.contains("MIN")) {
                          System.out.println("Recieved a candidate");
                          int i = Integer.parseInt(in.readLine());
                          String a = in.readLine();
                          String[] arr = a.split(":");
                          String[] ready = askers.get(i).split(":");
                          if(Integer.parseInt(arr[1]) < Integer.parseInt(ready[1]))
                              askers.replace(i, a);
                      }

                    //if recieved an answer to one of the searches
                    else {
                          int i = 0;
                          if (line != null) {
                              i = Integer.parseInt(line);
                          }
                          String a = in.readLine();
                        if(askers.containsKey(i))
                            askers.replace(i, a);
                    }

                    //udp connection for database queries
                    if (operation != null) {
                        int AskKey = ++identifier;
                        askers.put(AskKey, "ERROR");
                        switch (operation) {
                            case "set-value": {
                                System.out.println("executing set");
                                String[] parameters = parameter.split(":");
                                //if this node has it
                                if(key == Integer.parseInt(parameters[0])){
                                    value = Integer.parseInt(parameters[1]);
                                    out.println("OK");
                                    out.println();
                                }
                                else
                                    //if this node is not connected anywhere
                                    if(connections.isEmpty()) {
                                        out.println("ERROR");
                                        out.println();
                                    }
                                    else {
                                        //this node doesn't have it and asks neighbors
                                        byte[] queryBuff = ("SET " + parameter +
                                                " " + UDPport + ":" + UdpAddress + " " + AskKey + "\r\n").getBytes();
                                        for(Integer node : connections.keySet()){
                                            System.out.println("Contacting " + node + ":" + connections.get(node));
                                            DatagramPacket query = new DatagramPacket(queryBuff, queryBuff.length,
                                                    InetAddress.getByName(connections.get(node)), node);
                                            UDpserver.send(query);

                                            System.out.println("Waiting for server response...");
                                            synchronized (this) {
                                                try {
                                                    wait(timeout);
                                                } catch (InterruptedException e) {
                                                    throw new RuntimeException(e);
                                                }
                                            }
                                            if(!askers.get(AskKey).equals("ERROR")) {
                                                System.out.println("Received Response");
                                                break;
                                            }
                                        }
                                        out.println(askers.get(AskKey));
                                        out.println();
                                        askers.remove(AskKey);
                                    }
                            }
                            break;
                            case "get-value": {
                                System.out.println("executing get");
                                //if this node has it
                                if(key == Integer.parseInt(parameter)){
                                    out.println(key + ":" + value);
                                    out.println();
                                }
                                else
                                    //if this node is not conncected anywhere
                                    if(connections.isEmpty()) {
                                        out.println("ERROR");
                                        out.println();
                                    }
                                    else {
                                        //this node doesn't have it and asks neighbors
                                        byte[] queryBuff = ("GET " + parameter +
                                                " " + UDPport + ":" + UdpAddress + " " + AskKey + "\r\n").getBytes();
                                        for(Integer node : connections.keySet()){
                                            System.out.println("Contacting " + node + ":" + connections.get(node));
                                            DatagramPacket query = new DatagramPacket(queryBuff, queryBuff.length,
                                                    InetAddress.getByName(connections.get(node)), node);
                                            UDpserver.send(query);

                                            System.out.println("Waiting for server response...");
                                            synchronized (this) {
                                                try {
                                                    wait(timeout);
                                                } catch (InterruptedException e) {
                                                    throw new RuntimeException(e);
                                                }
                                            }
                                            if(!askers.get(AskKey).equals("ERROR")) {
                                                System.out.println("Received Response");
                                                break;
                                            }
                                        }
                                        out.println(askers.get(AskKey));
                                        out.println();
                                        askers.remove(AskKey);
                                    }
                            }
                            break;
                            case "find-key": {
                                System.out.println("executing find");
                                //if this node has it
                                if(key == Integer.parseInt(parameter)){
                                    out.println(UdpAddress + ":" + UDPport);
                                    out.println();
                                }
                                else
                                    //if this node is not conncected anywhere
                                    if(connections.isEmpty()) {
                                        out.println("ERROR");
                                        out.println();
                                    }
                                    else {
                                        //this node doesn't have it and asks neighbors
                                        byte[] queryBuff = ("FIND " + parameter +
                                                " " + UDPport + ":" + UdpAddress + " " + AskKey + "\r\n").getBytes();
                                        for(Integer node : connections.keySet()){
                                            System.out.println("Contacting " + node + ":" + connections.get(node));
                                            DatagramPacket query = new DatagramPacket(queryBuff, queryBuff.length,
                                                    InetAddress.getByName(connections.get(node)), node);
                                            UDpserver.send(query);

                                            System.out.println("Waiting for server response...");
                                            synchronized (this) {
                                                try {
                                                    wait(timeout);
                                                } catch (InterruptedException e) {
                                                    throw new RuntimeException(e);
                                                }
                                            }
                                            if(!askers.get(AskKey).equals("ERROR")) {
                                                System.out.println("Received Response");
                                                break;
                                            }
                                        }
                                        out.println(askers.get(AskKey));
                                        out.println();
                                        askers.remove(AskKey);
                                    }
                            }
                            break;
                            case "get-max": {
                                if(connections.isEmpty()){
                                    out.println(key + ":" + value);
                                    out.println();
                                }
                                else {
                                    int max = value;
                                    int maxKey = key;
                                    String visited = UdpAddress + "|" + UDPport;
                                    askers.replace(AskKey, maxKey + ":" + max + ":" + visited);
                                    for (Integer node : connections.keySet()) {
                                        String [] visitedarr = askers.get(AskKey).split(":");
                                        visited = visitedarr[2];
                                        if(visited.contains(connections.get(node) + "|" + node))
                                            continue;
                                        byte[] queryBuff = ("MAX " + visitedarr[0] + ":" + visitedarr[1] +
                                                " " + UDPport + ":" + UdpAddress + " " +
                                                AskKey + " " + visited + "\r\n").getBytes();
                                        System.out.println("Contacting " + node + ":" + connections.get(node));
                                        DatagramPacket query = new DatagramPacket(queryBuff, queryBuff.length,
                                                InetAddress.getByName(connections.get(node)), node);
                                        UDpserver.send(query);

                                        System.out.println("Waiting for server response...");
                                        synchronized (this) {
                                            try {
                                                wait(timeout);
                                            } catch (InterruptedException e) {
                                                throw new RuntimeException(e);
                                            }
                                        }
                                    }
                                    String[] resp = askers.get(AskKey).split(":");
                                    maxKey = Integer.parseInt(resp[0]);
                                    max = Integer.parseInt(resp[1]);
                                    out.println(maxKey + ":" + max);
                                    out.println();
                                }
                            }
                            break;
                            case "get-min": {
                                if(connections.isEmpty()){
                                    out.println(key + ":" + value);
                                    out.println();
                                }
                                else {
                                    int min = value;
                                    int minKey = key;
                                    String visited = UdpAddress + "|" + UDPport;
                                    askers.replace(AskKey, minKey + ":" + min + ":" + visited);
                                    for (Integer node : connections.keySet()) {
                                        String [] visitedarr = askers.get(AskKey).split(":");
                                        visited = visitedarr[2];
                                        if(visited.contains(connections.get(node) + "|" + node))
                                            continue;
                                        byte[] queryBuff = ("MIN " + visitedarr[0] + ":" + visitedarr[1] +
                                                " " + UDPport + ":" + UdpAddress + " " + AskKey +
                                                " " + visited + "\r\n").getBytes();
                                        System.out.println("Contacting " + node + ":" + connections.get(node));
                                        DatagramPacket query = new DatagramPacket(queryBuff, queryBuff.length,
                                                InetAddress.getByName(connections.get(node)), node);
                                        UDpserver.send(query);

                                        System.out.println("Waiting for server response...");
                                        synchronized (this) {
                                            try {
                                                wait(timeout);
                                            } catch (InterruptedException e) {
                                                throw new RuntimeException(e);
                                            }
                                        }
                                    }
                                    String[] resp = askers.get(AskKey).split(":");
                                    minKey = Integer.parseInt(resp[0]);
                                    min = Integer.parseInt(resp[1]);
                                    out.println(minKey + ":" + min);
                                    out.println();
                                }
                            }
                            break;
                            case "terminate": {
                                if(!connections.isEmpty()) {
                                    for (Integer port : connections.keySet()) {
                                        Socket socket1 = new Socket(connections.get(port), port);
                                        PrintWriter out1 = new PrintWriter(socket1.getOutputStream(), true);
                                        out1.println("Goodbye");
                                        out1.println("Disconnect:" + UDPport + ":" + UdpAddress);
                                        out1.println();
                                        socket1.close();
                                    }
                                }
                                out.println("OK");
                                out.println();
                                socket.close();
                                System.exit(1);
                            }
                            break;
                            case "new-record": {
                                String[] record = parameter.split(":");
                                key = Integer.parseInt(record[0]);
                                value = Integer.parseInt(record[1]);
                                out.println("OK");
                                out.println();
                            }
                            break;
                            default:{
                                System.out.println("Unknown Operation");
                            }
                        }
                    }
                } catch (IOException e1) {
                    System.out.println(e1.getMessage());
                    System.out.println("Something went wrong");
                }
                try {
                    socket.close();
                } catch (IOException e) {
                    System.out.println("Couldn't close");
                }
            System.out.println(Thread.currentThread().getName() + " exiting!");
            }
        }


    public static void main(String[] args) {
        int tcpPort = 0;

        //creation of the Process
        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "-tcpport":
                    tcpPort = Integer.parseInt(args[++i]);
                    break;
                case "-record":
                    String[] values = args[++i].split(":");
                    key = Integer.parseInt(values[0]);
                    value = Integer.parseInt(values[1]);
                    break;
                case "-connect": {
                    String[] connect = args[++i].split(":");
                    connections.put(Integer.parseInt(connect[1]), connect[0]);
                }
                break;
            }
        }

        createProcess(tcpPort);

    }
}
