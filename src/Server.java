package server;

import java.io.*;
import java.net.InetSocketAddress;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.HashMap;
import java.util.Scanner;
import java.sql.*;

public class Server {
    //stores all the rooms the server is hosting
    private HashMap<String, ChatRoom> rooms;
    //server socket channel listens to requests
    private ServerSocketChannel listener;
    //connection to the chat history database
    private Connection connection;

    private final int PORT_NUM = 8080;

    /**
     * Constructor, creates a new server and establishes a connection to the message database
     */
    Server() {
        rooms = new HashMap<>();
        try {
            listener = ServerSocketChannel.open();
            try{
                connection = DriverManager.getConnection("jdbc:sqlite:chatHistory.db");
            }catch(SQLException e) {
                e.printStackTrace();
            }

            //only run once to create database
//            connection = DriverManager.getConnection("jdbc:sqlite:chatHistory.db");
//            Statement s = connection.createStatement();
//            String template = "CREATE TABLE chatHistory (\n"
//                    + "room text,\n"
//                    + "user text,\n"
//                    + "msg text\n"
//                    + ");";
//
//            String msg = "INSERT INTO chatHistory(room, user, msg) VALUES('room', 'oliver', 'hello')";
//            s.executeUpdate(msg);
//            s.close();
//            Statement st = connection.createStatement();
//            ResultSet res = st.executeQuery("SELECT * FROM chatHistory");
//            while(res.next()) {
//                System.out.println(res.getString("room"));
//                System.out.println(res.getString("user"));
//                System.out.println(res.getString("msg"));
//            }

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Server opens up a new thread for each connection, if its a new room request it will
     * attemp to join the connection to an existing thread containing a room,
     * if the room does not exist then a new thread will be created to host the room.
     * Any subsequent client connections to a pre-existing room will simply have their connections
     * added to the existing thread without keeping a client request thread alive for the duration of their connection.
     */
    public void run() {
        try {
            //listen to port
            listener.bind(new InetSocketAddress(PORT_NUM));

            //we want ssc to be blocking while listening
            listener.configureBlocking(true);

            while (true) {
                //client socket
                SocketChannel client = listener.accept();

                //each new request is handled on a new thread
                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        try  {
                            OutputStream out = client.socket().getOutputStream();
                            Scanner in = new Scanner(client.socket().getInputStream(), "UTF-8");

                            //new request from client input
                            Request req = new Request(in);

                            //forms a response based on the request
                            Response res = new Response(req);

                            //sends out response
                            res.write(out);
                            out.flush();

                            //if the response is a websocket HS
                            if (req.isWebSocket()) {
                                WebSocketMsg msg = new WebSocketMsg(client.socket().getInputStream());
                                String[] dataFrame = new String(msg.getDecoded()).split("\\s+");

                                //check if they want to join a room
                                if (dataFrame[0].equals("join")) {
                                    //if joining room initialize a connection to the chat history database

                                    String roomName = dataFrame[1];

                                    //check if room exists
                                    checkIfRoomExists(roomName, client);
                                }
                            }else {
                                //if not a WS request close everything for the current thread
                                try{
                                    client.close();
                                }catch(IOException e) {
                                    e.printStackTrace();
                                }
                                out.close();
                                in.close();
                            }

                        } catch (IOException | BadRequestException e) {
                            e.printStackTrace();
                        }
                    }
                }).start();
            }
        } catch (IOException e) {
            e.printStackTrace();
            return;
        } finally {
            //close server socket
            try {
                listener.close();
            } catch (IOException e) {
                e.printStackTrace();
                return;
            }
        }
    }

    /**
     * checks if the room requested is already running
     * @param roomName
     * @param client
     * @throws IOException
     */
    private void checkIfRoomExists(String roomName, SocketChannel client) throws IOException {
        if (rooms.containsKey(roomName)) {
            rooms.get(roomName).addSocketChannel(client);
        } else {
            ChatRoom room = new ChatRoom(connection, roomName);
            room.addSocketChannel(client);
            rooms.put(roomName, room);
            room.listen();
        }

        //when the room becomes empty we can remove the room from the server altogether
        if(rooms.get(roomName).hasClosed()) {
            rooms.remove(roomName);
        }
    }
}