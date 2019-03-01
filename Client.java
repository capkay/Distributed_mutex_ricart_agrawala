import java.util.Date;
import java.util.concurrent.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.LinkedList;
import java.util.*;
import java.lang.management.*;
import java.lang.*;
import java.net.InetAddress;
import java.security.MessageDigest;
import java.text.*;
import java.io.*;
import java.net.*;
import java.util.HashMap;
import java.util.Map;
import java.util.Iterator;
import java.util.Set;

// ClientNode class : handles both server/client connections to other P2P nodes and handle content shared/published over this network 
class ClientNode 
{
    // server socket variables 
    ServerSocket server = null; 	// server socket used by PeerWorker, socket for maintaining the P2P network
    final int local_d = 1;
    // table that maintains list of files
    //List<GLBInfo> world_content	= new LinkedList<GLBInfo>();
    // table that maintains neighbors of this node 
    //List<IPData> neighbors	= new LinkedList<IPData>();
    HashMap<Integer, SockHandle> s_list = new HashMap<Integer, SockHandle>();
    ClientNode cnode = null;
    // variables to obtain IP/port information of where the node is running
    InetAddress myip = null;
    String hostname = null;
    String ip = null;
    String port = null;
    int c_id = -1;
    RAlgorithm ra_inst = null;
    ClientNode(String port, int c_id)
    {
    	this.port = port;
        this.c_id = c_id;
        this.listenSocket();
        this.cnode = this;
    }
    
    // CommandParser class is used to parse and execute respective commands that are entered via command line to initialize/publish/unpublish/query from an establised P2P node
    public class CommandParser extends Thread
    {
      	// initialize patters for commands
    	Pattern SETUP = Pattern.compile("^SETUP$");
    	Pattern LIST  = Pattern.compile("^LIST$");
    	Pattern START = Pattern.compile("^START$");
    	Pattern FINISH= Pattern.compile("^FINISH$");
    	
    	// read from inputstream, process and execute tasks accordingly	
    	int rx_cmd(Scanner cmd)
        {
    		String cmd_in = null;
    		if (cmd.hasNext())
    			cmd_in = cmd.nextLine();
    
    		Matcher m_START= START.matcher(cmd_in);
    		Matcher m_LIST= LIST.matcher(cmd_in);
    		Matcher m_SETUP= SETUP.matcher(cmd_in);
    		Matcher m_FINISH= FINISH.matcher(cmd_in);
    		
    		// PEER <IP> <port> , create instance of PeerClient to connect to respective IP/port and add to neighbor list	
    		if(m_SETUP.find())
                { 
                    setup_connections();
    		}
                else if(m_LIST.find())
                { 
                    synchronized (s_list)
                    {
                        System.out.println("\n=== Iterating over the HashMap's keySet ===");
                        s_list.keySet().forEach(key -> {
                        System.out.println("key:"+key + " => " + s_list.get(key).remote_c_id);
                        });
                        System.out.println("=== size ="+s_list.size());
                    }
    		}
                else if(m_START.find())
                { 
                    for(int i=0;i<50;i++)
                    {
                        randomDelay(0.005,1.25);
                        request_crit_section();
                    }
    		}
                else if(m_FINISH.find())
                { 
    		    System.out.println("finished executing");
                    return 0;
    		}
    		// default message
    		else 
                {
    		    System.out.println("Unknown command entered : please enter a valid command");
    		}
    		
    		// to loop forever	
    		return 1;
    	}
    
    	public void run() {
    		System.out.println("Enter commands: SETUP / START");
    		Scanner input = new Scanner(System.in);
    		while(rx_cmd(input) != 0) { }  // to loop forever
    	}
    }
    void randomDelay(double min, double max)
    {
        int random = (int)(max * Math.random() + min);
        try 
        {
            Thread.sleep(random * 1000);
        } 
        catch (InterruptedException e) 
        {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }
    public void request_crit_section()
    {
        System.out.println("\n=== Initiate REQUEST ===");
        ra_inst.request_resource();
        System.out.println("Entering critical section of client "+ c_id);
        randomDelay(0.5,4.25);
        System.out.println("Finished critical section of client "+ c_id);
        ra_inst.release_resource();
    }
    public void create_RAlgorithm()
    {
        ra_inst = new RAlgorithm(cnode,c_id);
        if(c_id == 0)
        {
            synchronized (s_list)
            {
                s_list.keySet().forEach(key -> {
                    s_list.get(key).send_setup_finish();
                });
            }
        }
    }

    public void setup_connections()
    {
        ClientInfo t = new ClientInfo();
        for(int i=0;i<5;i++)
        {
            if(i > c_id)
            {
                //System.out.println(t.hmap.get(i).ip);
                //System.out.println(t.hmap.get(i).port);
                String t_ip = t.hmap.get(i).ip;
                int t_port = Integer.valueOf(t.hmap.get(i).port);
                Thread x = new Thread()
                {
            	public void run()
                    {
                        try
                        {
                            Socket s = new Socket(t_ip,t_port);
                            SockHandle t = new SockHandle(s,ip,port,c_id,s_list,false,cnode);
                        }
                        catch (UnknownHostException e) 
                        {
                        	System.out.println("Unknown host");
                        	//System.exit(1);
                        } 
                        catch (IOException e) 
                        {
                        	System.out.println("No I/O");
                        	//System.exit(1);
                            e.printStackTrace(); 
                        }
            	}
                };
                    
                x.setDaemon(true); 	// terminate when main ends
                x.start(); 			// start the thread


            }
        }
                Thread y = new Thread()
                {
            	public void run()
                    {
                int size = 0;
                while (size != 4){
                    synchronized(s_list){
		    //System.out.println("sync"+size);
                    size = s_list.size();}
                }
                s_list.get(c_id+1).send_setup();
		    System.out.println("chain setup init");
            	}
                };
                    
                y.setDaemon(true); 	// terminate when main ends
                y.start(); 			// start the thread
    }
    // method to start server and listen for incoming connections
    public void listenSocket()
    {
        // create server socket on specified port number
        try
        {
            // create server socket and display host/addressing information of this node
            server = new ServerSocket(Integer.valueOf(port)); 
            System.out.println("ClientNode running on port " + port +"," + " use ctrl-C to end");
            myip = InetAddress.getLocalHost();
            ip = myip.getHostAddress();
            hostname = myip.getHostName();
            System.out.println("Your current IP address : " + ip);
            System.out.println("Your current Hostname : " + hostname);
        } 
        catch (IOException e) 
        {
            System.out.println("Error creating socket");
            System.exit(-1);
        }

	// create instance of commandparser thread and start it	
	CommandParser cmdpsr = new CommandParser();
	cmdpsr.start();
        

        // create thread to handle incoming connections
        Thread accept = new Thread() 
        {
            public void run()
            {
                while(true)
                {
                    try
                    {
                        Socket s = server.accept();
                	SockHandle t = new SockHandle(s,ip,port,c_id,s_list,true,cnode);
                    }
                    catch (UnknownHostException e) 
		    {
		    	System.out.println("Unknown host");
		    	//System.exit(1);
		    } 
		    catch (IOException e) 
		    {
		    	System.out.println("No I/O");
		    	//System.exit(1);
                        e.printStackTrace(); 
		    }

                }
            }
        };
        accept.setDaemon(true);
        accept.start();
    }
    
    public static void main(String[] args)
    {
    	// check for valid number of command line arguments
    	if (args.length != 2)
    	{
    	    System.out.println("Usage: java ClientNode <port-number> <client-id>");
    	    System.exit(1);
    	}
    	ClientNode server = new ClientNode(args[0], Integer.valueOf(args[1]));
    	//server.listenSocket();
    }
}
