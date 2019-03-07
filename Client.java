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
    HashMap<Integer, SockHandle> c_list = new HashMap<Integer, SockHandle>();
    HashMap<Integer, SockHandle> s_list = new HashMap<Integer, SockHandle>();
    HashMap<String, RAlgorithm> r_list = new HashMap<String, RAlgorithm>();
    List<String> files = new ArrayList<String>();
    ClientNode cnode = null;
    // variables to obtain IP/port information of where the node is running
    InetAddress myip = null;
    String hostname = null;
    String ip = null;
    String port = null;
    int c_id = -1;
    ClientInfo c_info = null;
    ServerInfo s_info = null;
    ClientNode(int c_id)
    {
        this.c_info = new ClientInfo();
        this.s_info = new ServerInfo();
        this.c_id = c_id;
    	this.port = c_info.hmap.get(c_id).port;
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
    	Pattern ENQUIRE= Pattern.compile("^ENQUIRE$");
    	
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
    		Matcher m_ENQUIRE= ENQUIRE.matcher(cmd_in);
    		
    		// PEER <IP> <port> , create instance of PeerClient to connect to respective IP/port and add to neighbor list	
    		if(m_SETUP.find())
                { 
                    setup_connections();
    		}
                else if(m_ENQUIRE.find())
                { 
                    initiate_enquiry();
                }
                else if(m_LIST.find())
                { 
                    synchronized (c_list)
                    {
                        System.out.println("\n=== Clients ===");
                        c_list.keySet().forEach(key -> {
                        System.out.println("key:"+key + " => ID " + c_list.get(key).remote_c_id);
                        });
                        System.out.println("=== size ="+c_list.size());
                    }
                    synchronized (s_list)
                    {
                        System.out.println("\n=== Servers ===");
                        s_list.keySet().forEach(key -> {
                        System.out.println("key:"+key + " => ID " + s_list.get(key).remote_c_id);
                        });
                        System.out.println("=== size ="+s_list.size());
                    }
    		}
                else if(m_START.find())
                { 
    		    System.out.println("START Random READ/WRITE simulation");
                    for(int i=0;i<50;i++)
                    {
                        randomDelay(0.005,1.25);
                        request_crit_section();
                    }
    		    System.out.println("FINISH Random READ/WRITE simulation");
    		}
                else if(m_FINISH.find())
                { 
    		    System.out.println("Closing connections and exiting program!");
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
    public void initiate_enquiry()
    {
        int size = 0;
        while (size != 3)
        {
            synchronized(s_list)
            {
                size = s_list.size();
            }
        }
        int random = (int)(3 * Math.random() + 0);
        System.out.println("Enquiring server : "+random+" for files");
        synchronized (s_list)
        {
            s_list.get(random).enquire_files();
        }
        print_enquiry_results();
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
    public void print_enquiry_results()
    {
	System.out.println("Files available:");
	for (int i = 0; i < files.size(); i++) {
		System.out.println(files.get(i));
	}
    }
    public void request_crit_section()
    {
        System.out.println("\n=== Initiate REQUEST ===");
        int rf = (int)( (files.size()) * Math.random() + 0);
        String filename = files.get(rf);
        System.out.println("=== Chosen file = "+filename);
        r_list.get(filename).request_resource();
        System.out.println("Entering critical section of client "+ c_id);
        int random = (int)(2 * Math.random() + 0);
        int rs = (int)(3 * Math.random() + 0);
        int timestamp = 0;
        synchronized(r_list.get(filename).cword)
        {
            timestamp = r_list.get(filename).cword.our_sn;
            System.out.println("Critical section timestamp :"+timestamp);
        }
        synchronized (s_list)
        {
            if(random == 0)
            {
                    System.out.println("READ sent to server :"+rs);
                    s_list.get(rs).read_file(filename);
            }
            else
            {
                    s_list.keySet().forEach(key -> 
                    {
                        System.out.println("WRITE sent to server :"+key);
                        s_list.get(key).write_file(filename);
                    });
            }
        }
        //randomDelay(0.5,4.25);
        System.out.println("Finished critical section of client "+ c_id);
        r_list.get(filename).release_resource();
    }
    public void create_RAlgorithm()
    {
	for (int i = 0; i < files.size(); i++) {
	    System.out.println("ME for file :"+files.get(i));
	    String temp = files.get(i);
            RAlgorithm rx = new RAlgorithm(cnode,c_id,temp);
            r_list.put(temp,rx);
	}
        if(c_id == 0)
        {
            synchronized (c_list)
            {
                c_list.keySet().forEach(key -> {
                    c_list.get(key).send_setup_finish();
                });
            }
        }
    }

    public void setup_servers()
    {
        for(int i=0;i<3;i++)
        {
            //System.out.println(c_info.hmap.get(i).ip);
            //System.out.println(c_info.hmap.get(i).port);
            String t_ip = s_info.hmap.get(i).ip;
            int t_port = Integer.valueOf(s_info.hmap.get(i).port);
            Thread x = new Thread()
            {
            public void run()
                {
                    try
                    {
                        Socket s = new Socket(t_ip,t_port);
                        SockHandle t = new SockHandle(s,ip,port,c_id,c_list,s_list,false,true,cnode);
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
            x.setName("Client_"+c_id+"_SockHandle_to_Server"+i);
            x.start(); 			// start the thread
        }
    }
    public void setup_clients()
    {
        //ClientInfo c_info = new ClientInfo();
        for(int i=0;i<5;i++)
        {
            if(i > c_id)
            {
                //System.out.println(c_info.hmap.get(i).ip);
                //System.out.println(c_info.hmap.get(i).port);
                String t_ip = c_info.hmap.get(i).ip;
                int t_port = Integer.valueOf(c_info.hmap.get(i).port);
                Thread x = new Thread()
                {
            	public void run()
                    {
                        try
                        {
                            Socket s = new Socket(t_ip,t_port);
                            SockHandle t = new SockHandle(s,ip,port,c_id,c_list,s_list,false,false,cnode);
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
                x.setName("Client_"+c_id+"_SockHandle_to_Client"+i);
                x.start(); 			// start the thread


            }
        }
                Thread y = new Thread()
                {
            	public void run()
                    {
                int size = 0;
                while (size != 4){
                    synchronized(c_list){
		    //System.out.println("sync"+size);
                    size = c_list.size();}
                }
                c_list.get(c_id+1).send_setup();
		    System.out.println("chain setup init");
            	}
                };
                    
                y.setDaemon(true); 	// terminate when main ends
                y.start(); 			// start the thread
    }

    public void setup_connections()
    {
        setup_servers();
        setup_clients();
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
                	SockHandle t = new SockHandle(s,ip,port,c_id,c_list,s_list,true,false,cnode);
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
        accept.setName("AcceptClient_"+c_id+"_SockHandle_to_Client");
        accept.start();
    }
    
    public static void main(String[] args)
    {
    	// check for valid number of command line arguments
    	if (args.length != 1)
    	{
    	    System.out.println("Usage: java ClientNode <client-id>");
    	    System.exit(1);
    	}
    	ClientNode server = new ClientNode(Integer.valueOf(args[0]));
    	//server.listenSocket();
    }
}
