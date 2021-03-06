import java.util.Date;
import java.util.concurrent.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.LinkedList;
import java.lang.management.*;
import java.net.InetAddress;
import java.security.MessageDigest;
import java.text.*;
import java.io.*;
import java.net.*;
import java.util.HashMap;
import java.util.Map;
import java.util.Iterator;
import java.util.Set;

// ServerSockHandle class, to handle each socket connection to all clients
class ServerSockHandle
{
    // to send data out
    PrintWriter out = null;
    // to read incoming data
    BufferedReader in = null;
    // socket instance for the connecting client
    Socket client;
    // variables to obtain IP/port information of where the node is running
    String ip = null; 			// remote ip address
    String port = null; 		// remote port 
    String my_ip = null; 		// ip of this node
    String my_port = null; 		// port of this node
    // variable to store ID of owning client
    int my_c_id = -1;
    // variable to store ID of remote client
    int remote_c_id = -1;
    // hash table handles to hold all client connections
    HashMap<Integer, ServerSockHandle> s_list = null;
    // boolean flag: functionality change if this is created by a listening node
    boolean rx_hdl = false;
    // handle to server object, to trigger some server methods
    ServerNode snode = null;
    // generic 'end of message' pattern 
    public static Pattern eom = Pattern.compile("^EOM");  // generic 'end of message' pattern 
    
    // constructor to connect respective variables
    ServerSockHandle(Socket client,String my_ip,String my_port,int my_c_id,HashMap<Integer, ServerSockHandle> s_list, boolean rx_hdl,ServerNode snode) 
    {
    	this.client  = client;
    	this.my_ip = my_ip;
    	this.my_port = my_port;
        this.my_c_id = my_c_id;
        this.remote_c_id = remote_c_id;
        this.s_list = s_list;
        this.rx_hdl = rx_hdl;
        this.snode = snode;
        // get input and output streams from socket
    	try 
    	{
    	    in = new BufferedReader(new InputStreamReader(client.getInputStream()));
    	    out = new PrintWriter(client.getOutputStream(), true);
    	} 
    	catch (IOException e) 
    	{
    	    System.out.println("in or out failed");
    	    System.exit(-1);
    	}
        try
        {
            // only when this is started from a listening node
            // send a initial_setup_server message to the initiator node (like an acknowledgement message)
            // and get some information from the remote initiator node
            if(rx_hdl == true)
            {
    	        System.out.println("send cmd 1: setup socket to client");
                out.println("initial_setup_server");
                ip = in.readLine();
    	        System.out.println("ip:"+ip);
                port=in.readLine();
    	        System.out.println("port:"+port);
                remote_c_id=Integer.valueOf(in.readLine());
    	        out.println(my_ip);
    	        out.println(my_port);
    	        out.println(my_c_id);
    	        System.out.println("neighbor client connection, PID:"+ Integer.toString(remote_c_id)+ " ip:" + ip + " port = " + port);
                // when this handshake is done
                // add this object to the socket handle list as part of the main server object
                synchronized (s_list)
                {
                    s_list.put(remote_c_id,this);
                }
            }
        }
    	catch (IOException e)
    	{
    	    System.out.println("Read failed");
    	    System.exit(1);
    	}
    	// handle unexpected connection loss during a session
    	catch (NullPointerException e)
    	{
    	    System.out.println("peer connection lost");
    	    System.exit(1);
    	}
        // thread that continuously runs and waits for incoming messages
        // to process it and perform actions accordingly
    	Thread read = new Thread()
        {
    	    public void run()
            {
    	        while(rx_cmd(in,out) != 0) { }
            }
    	};
    	read.setDaemon(true); 	// terminate when main ends
        read.start();		// start the thread	
    }
    
    // method to process incoming commands and data associated with them
    public int rx_cmd(BufferedReader cmd,PrintWriter out)
    {
    	try
    	{
            // get blocked in readLine until something actually comes on the inputStream
            // then perform actions based on the received command
    	    String cmd_in = cmd.readLine();
            // initial_setup_server sequence to populate the client socket list stored by the server
    	    if(cmd_in.equals("initial_setup_server"))
            {
    	        System.out.println("got cmd 1");
    	        out.println(my_ip);
    	        out.println(my_port);
    	        out.println(my_c_id);
                ip = in.readLine();
    	        System.out.println("ip:"+ip);
                port=in.readLine();
    	        System.out.println("port:"+port);
                remote_c_id=Integer.valueOf(in.readLine());
    	        System.out.println("server connection, PID:"+ Integer.toString(remote_c_id)+ " ip:" + ip + " port = " + port);
                synchronized (s_list)
                {
                    s_list.put(remote_c_id,this);
                }
    	    }
            // to terminate the program
            else if(cmd_in.equals("simulation_finish"))
            {
    	        System.out.println("Finish program execution!");
                snode.end_program();
                return 0;
            }
            // send file metadata one by one to the requesting client
            else if(cmd_in.equals("ENQUIRY"))
            {
    	        System.out.println("Received ENQUIRY from client :"+ remote_c_id);
                synchronized(snode.files)
                {
    	            for (int i = 0; i < snode.files.size(); i++) 
                    {
    	        	out.println(snode.files.get(i));
    	                System.out.println("file :"+ snode.files.get(i));
    	            }
                }
                // EOM helps in terminating loop at the receiver site
                out.println("EOM");
            }
            // perform the read operation for the requested file
            else if(cmd_in.equals("READ"))
            {
                String filename = in.readLine();
                String content = snode.do_read_operation(filename);
                out.println(content);
            }
            // perform the write operation for the requested file
            else if(cmd_in.equals("WRITE"))
            {
                String filename = in.readLine();
                String content = in.readLine();
                snode.do_write_operation(filename,content);
                out.println("EOM");
            }
    	}
    	catch (IOException e) 
    	{
    	    System.out.println("Read failed");
    	    System.exit(-1);
    	}
    
    	// default : return 1, to continue processing further commands 
    	return 1;
    }
}
