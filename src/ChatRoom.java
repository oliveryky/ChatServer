package server;
import java.io.IOException;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.sql.*;
import java.util.*;

public class ChatRoom {
    //list of the room's message history
    private LinkedList<byte[]> msgHistory;
    //list of clients waiting to be registered
    private LinkedList<SocketChannel> clientQueue;
    //set of all the clients that are connected to the room
    private HashSet<SocketChannel> clients;
    //selector for the room
    private Selector selector;
    //a connection to the data base
    private Connection connection;
    //current room's name
    private String roomName;

    private boolean hasClosed;

    /**
     * ChatRoom constructor
     * Creates a new room or if it's reopening a new room, try to get all messages from a previous session
     * from the database.
     */
    public ChatRoom(Connection connection, String roomName) {
        clients = new HashSet<>();
        msgHistory = new LinkedList<>();
        clientQueue = new LinkedList<>();
        this.connection = connection;
        this.roomName = roomName;
        hasClosed = false;

        try {
            Statement stmt = this.connection.createStatement();
            ResultSet res = stmt.executeQuery("SELECT * FROM chatHistory");
            while(res.next()) {
                if(res.getString("room").equals(roomName)) {
                    msgHistory.offer(new WebSocketMsg(res.getString("user"), res.getString("msg")).getEncoded());
                }
            }
            selector = Selector.open();
        }catch(SQLException | IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Listens to all the clients in the room for incoming messages and updates all clients with new messages.
     * A socketchannel is used here b/c once we start listening for websocket messages the thread becomes blocked.
     * A selector is used to keep track of multiple socket channels which will return a collection of all channels
     * that have data that is ready.
     *
     * Each channel is registered with the selector using the READ key. Channels must be in non-blocking mode.
     * Once it has valid data we remove the channel from the selector and set the channel to blocking mode.
     * This enables use to the get relevant data from that channel before setting it back to non-blocking mode to
     * re-register with the selector.
     *
     * Will only add new clients to the room after the selector finishes with the current round of valid channels.
     * @throws IOException
     */
    public void listen() throws IOException{
        while(true) {
            System.out.println("Listening");

            //prepare selector
            selector.select();
            Set<SelectionKey> selections = selector.selectedKeys();
            Iterator<SelectionKey> iter = selections.iterator();
            //go through selector
            while(iter.hasNext()) {
                SelectionKey key = iter.next();

                //if key is readable
                if(key.isReadable()) {
                    //remove from selector
                    iter.remove();
                    SocketChannel channel = (SocketChannel) key.channel();
                    key.cancel();
                    //set channel to blocking
                    channel.configureBlocking(true);

                    //get msg sent
                    WebSocketMsg msg = new WebSocketMsg(channel.socket().getInputStream());

                    if(!msg.isClosed()) {
                        //add msg to room history
                        msgHistory.offer(msg.getEncoded());

                        //add msg to server chat history;
                        addToDB(msg);
                        //set to non-blocking to re-register
                        channel.configureBlocking(false);
                        selector.selectNow();
                        channel.register(selector, SelectionKey.OP_READ);

                        //updates all clients in the room with the new message
                        update(msg);
                    }else {
                        //if the websocket connection is closed then remove
                        //and close the connection from the room
                        clients.remove(channel);
                        channel.socket().close();
                        if(clients.isEmpty()) {
                            hasClosed = true;
                            System.out.println("Closing room");
                            return;
                        }
                    }
                }
            }

            //add all clients waiting to join the room if any
            SocketChannel toBeAdded;
            while (!clientQueue.isEmpty()) {
                toBeAdded = clientQueue.pollFirst();
                toBeAdded.configureBlocking(true);

                //updates the new user with all the msgs of the room so far
                sendAll(toBeAdded);
                System.out.println(toBeAdded);
                toBeAdded.configureBlocking(false);

                //registers the channel
                toBeAdded.register(selector, SelectionKey.OP_READ);
                clients.add(toBeAdded);
            }
        }
    }

    /**
     * adds a socket channel to the current room
     * need to be synchronized so that we don't concurrently modify the client queue
     * when two users attemp to join the room at the same time.
     * @param sc
     */
    public synchronized void addSocketChannel(SocketChannel sc) {
        clientQueue.offer(sc);
        selector.wakeup();
    }

    /**
     * Updates all clients connected to the room with the new message
     * @param msg
     */
    private synchronized void update(WebSocketMsg msg) {
        try {
            selector.selectNow();
            for (SocketChannel sc : clients) {
                SelectionKey key = sc.keyFor(selector);
                key.cancel();
                sc.configureBlocking(true);

                sc.socket().getOutputStream().write(msg.getEncoded());
                sc.socket().getOutputStream().flush();

                sc.configureBlocking(false);
                selector.selectNow();
                sc.register(selector, SelectionKey.OP_READ);
            }
        }catch(IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * sends a user all the messages so far
     * Synchronized b/c we don't want to be reading and writing from the same user at the same time
     * @param sc
     */
    private synchronized void sendAll(SocketChannel sc) {
        for(byte[] msg: msgHistory) {
            try {
                sc.socket().getOutputStream().write(msg);
                sc.socket().getOutputStream().flush();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * adds the msg to the chat history database
     * Synchronized b/c we don't want multiple rooms trying to add to the same database at the same time
     * @param msg
     */
    private synchronized void addToDB(WebSocketMsg msg) {
        try{
            PreparedStatement pstmt = connection.prepareStatement("INSERT INTO chatHistory(room, user, msg) VALUES(?, ?, ?)");

            pstmt.setString(1, roomName);
            pstmt.setString(2, msg.getUserName());
            pstmt.setString(3, msg.getMsg());

            pstmt.executeUpdate();
            pstmt.close();
        }catch(SQLException e) {
            e.printStackTrace();
        }
    }

    /**
     * @return true if the room has no active users
     */
    public boolean hasClosed() {
        return hasClosed;
    }
}