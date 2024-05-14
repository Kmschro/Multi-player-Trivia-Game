import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;

public class Server {
    private static final int portNumber = 12345;
    private static ConcurrentLinkedQueue<String> messageQueue = new ConcurrentLinkedQueue<>();
    private static List<TriviaQuestion> triviaQuestions;
    private static int currentQuestionIndex = 0;
    private static boolean receivingPoll = true;
    private static List<ClientThread> ClientThreads = new ArrayList<>();
    public static int numClientsOutOfTime = 0;
    private static boolean hasPrintedWinners = false;

    public static void main(String[] args) {
        triviaQuestions = new ArrayList<>();
        try {
            readInFile("qAndA.txt");
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }

        try (ServerSocket serverSocket = new ServerSocket(portNumber)) {

            System.out.println("Server started. Waiting for clients to connect...");
            UDPThread udpThread = new UDPThread();
            udpThread.start();

            while (true) {
                Socket clientSocket = serverSocket.accept();
                ClientThread ClientThread = new ClientThread(clientSocket);
                ClientThreads.add(ClientThread);
                System.out.println("Client connected: " + clientSocket.getRemoteSocketAddress().toString());

                new Thread(() -> {
                    try {
                        sendCurrentQuestionToClient(ClientThread);
                        ClientThread.listenForMessages();
                    } catch (IOException e) {
                        System.out.println("An error occurred with a client connection.");
                        e.printStackTrace();
                    }
                }).start();
            }
        } catch (IOException e) {
            System.out.println("An error occurred starting the server.");
            e.printStackTrace();
        }
    }

    private static class UDPThread extends Thread {
        private DatagramSocket socket;
        private boolean running;
        private byte[] buf = new byte[256];

        public UDPThread() throws SocketException {
            socket = new DatagramSocket(portNumber);
        }

        public void run() {
            running = true;
            while (running) {
                DatagramPacket packet = new DatagramPacket(buf, buf.length);
                try {
                    socket.receive(packet);

                    String received = new String(packet.getData(), 0, packet.getLength());

                    InetAddress address = packet.getAddress();
                    int port = packet.getPort();
                    System.out.println(
                            "Received: " + received + " from: " + address.getHostAddress() + ":" + port);
                    if (receivingPoll) {
                        receivingPoll = false;
                        if (messageQueue.size() == 0) {
                            ClientThread matchingHandler = null;
                            for (ClientThread handler : ClientThreads) {
                                if (handler.getSocket().getInetAddress().equals(address)) {
                                    matchingHandler = handler;
                                    break;
                                }
                            }
                            if (matchingHandler != null) {
                                System.out.println("Sending ACK to " + address.getHostAddress());
                                try {
                                    matchingHandler.send("ACK");
                                    startClientTimer("10", matchingHandler);
                                    matchingHandler.setCanAnswer(true);
                                } catch (IOException e) {
                                    e.printStackTrace();
                                }
                            } else {
                                System.out.println("No matching client found for " + address.getHostAddress());
                            }
                        }
                    } else {
                        ClientThread matchingHandler = null;
                        for (ClientThread handler : ClientThreads) {
                            if (handler.getSocket().getInetAddress().equals(address)) {
                                matchingHandler = handler;
                                break;
                            }
                        }
                        if (matchingHandler != null) {
                            System.out.println("Sending NAK to " + address.getHostAddress());
                            try {
                                matchingHandler.send("NAK");
                                System.out.println("Sent NAK to " + address.getHostAddress());
                            } catch (IOException e) {
                                System.out.println(matchingHandler.getSocket() + "has closed");
                            }
                        } else {
                            System.out.println("No matching TCP client found for " + address.getHostAddress());
                        }
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            socket.close();
        }
    }

    // reads in file and adds String question, List<String> options, String
    // correctAnswer to arraylist of trivaQuestions
    public static void readInFile(String path) throws FileNotFoundException {
        File file = new File(path);
        if (!file.exists())
            throw new FileNotFoundException();

        Scanner reader = new Scanner(file);
        while (reader.hasNextLine()) {
            String str = reader.nextLine();
            if (!str.isEmpty()) {
                String question = str;
                List<String> options = new ArrayList<>();
                options.add(reader.nextLine());
                options.add(reader.nextLine());
                options.add(reader.nextLine());
                options.add(reader.nextLine());
                String correctAnswer = reader.nextLine();
                triviaQuestions.add(new TriviaQuestion(question, options, correctAnswer));
            }
        }
        reader.close();
    }

    private static void sendCurrentQuestionToClient(ClientThread ClientThread) throws IOException {

        if (currentQuestionIndex < triviaQuestions.size()) {
            startClientTimer("15", ClientThread);
            TriviaQuestion currentQuestion = triviaQuestions.get(currentQuestionIndex);
            String questionData = "Q" + currentQuestion.toString();
            try {
                ClientThread.send(questionData);
            } catch (Exception e) {
                System.out.println(ClientThread.getSocket() + "has closed");
            }

            ClientThread.setCorrectAnswer(currentQuestion.getCorrectAnswer());
        } else {
            System.out.println("end of game");
            if (!hasPrintedWinners) {
                printWinners();
                hasPrintedWinners = true;
            }

            try {
                ClientThread.send("END");
            } catch (Exception e) {
                System.out.println(ClientThread.getSocket() + "has closed");
            }
        }
    }

    public static void moveAllToNextQuestion() throws IOException {
        numClientsOutOfTime = 0;
        startAllClientsTimers("15");
        receivingPoll = true;
        messageQueue.clear();
        currentQuestionIndex++;
        List<ClientThread> safeList = new ArrayList<>(ClientThreads);

        for (ClientThread ClientThread : safeList) {
            sendCurrentQuestionToClient(ClientThread);
            ClientThread.setCanAnswer(false);
        }
    }

    public static synchronized void removeClient(ClientThread ClientThread) {
        ClientThreads.remove(ClientThread);
    }

    public static void startAllClientsTimers(String time) throws IOException {
        for (ClientThread ClientThread : ClientThreads) {
            try {
                ClientThread.send("Time " + time);
            } catch (Exception e) {
                System.out.println(ClientThread.getSocket() + "has closed");
            }

        }
    }

    public static void startClientTimer(String time, ClientThread ClientThread) throws IOException {
        try {
            ClientThread.send("Time " + time);
        } catch (Exception e) {
            System.out.println(ClientThread.getSocket() + "has closed");
        }

    }

    public static synchronized void clientOutOfTime(ClientThread ClientThread) {
        numClientsOutOfTime++;

        if (numClientsOutOfTime >= ClientThreads.size()) {
            System.out.println("All Clients out of time");
            numClientsOutOfTime = 0;
            try {
                if (currentQuestionIndex < triviaQuestions.size()) {
                    moveAllToNextQuestion();
                } else {
                    try {
                        ClientThread.send("END");
                    } catch (Exception e) {
                        System.out.println(ClientThread.getSocket() + "has closed");
                    }

                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public static void printWinners() {
        if (ClientThreads.isEmpty()) {
            System.out.println("No participants in the game.");
            return;
        }
        Collections.sort(ClientThreads);
        System.out.println("Winner:");
        System.out.println(ClientThreads.get(0).getClientId() + " with a score of " + ClientThreads.get(0).getScore());

        System.out.println("Final Scores:");
        for (ClientThread client : ClientThreads) {
            System.out.println(client.getClientId() + ": " + client.getScore());
        }
    }

}
