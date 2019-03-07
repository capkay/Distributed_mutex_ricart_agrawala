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

// SockHandle class, to handle each socket connection (to/from client/server)
class SockHandle
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
    // hash table handles to hold all client and server socket connections
    HashMap<Integer, SockHandle> c_list = null;
    HashMap<Integer, SockHandle> s_list = null;

    // boolean flag: functionality change if this is created by a listening node
    boolean rx_hdl = false;
    // boolean flag: functionality change if this is a connection to a server node
    boolean svr_hdl = false;
    // handle to client object, to trigger some client methods
    ClientNode cnode = null;
    // generic 'end of message' pattern 
    public static Pattern eom = Pattern.compile("^EOM");
    
    // constructor to connect respective variables
    SockHandle(Socket client,String my_ip,String my_port,int my_c_id,HashMap<Integer, SockHandle> c_list,HashMap<Integer, SockHandle> s_list, boolean rx_hdl,boolean svr_hdl,ClientNode cnode) 
    {
    	this.client  = client;
    	this.my_ip = my_ip;
    	this.my_port = my_port;
        this.my_c_id = my_c_id;
        this.remote_c_id = remote_c_id;
        this.c_list = c_list;
        this.s_list = s_list;
        this.rx_hdl = rx_hdl;
        this.svr_hdl = svr_hdl;
        this.cnode = cnode;
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
            // send a initial_setup message to the initiator node (like an acknowledgement message)
            // and get some information from the remote initiator node
            if(rx_hdl == true)
            {
    	        System.out.println("send cmd 1: setup sockets to other clients");
                out.println("initial_setup");
                ip = in.readLine();
    	        System.out.println("ip:"+ip);
                port=in.readLine();
    	        System.out.println("port:"+port);
                remote_c_id=Integer.valueOf(in.readLine());
    	        out.println(my_ip);
    	        out.println(my_port);
    	        out.println(my_c_id);
    	        System.out.println("neighbor connection, PID:"+ Integer.toString(remote_c_id)+ " ip:" + ip + " port = " + port);
                // when this handshake is done
                // add this object to the socket handle list as part of the main Client object
                synchronized (c_list)
                {
                    c_list.put(remote_c_id,this);
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
        read.setName("rx_cmd_"+my_c_id+"_SockHandle_to_Server"+svr_hdl);
        read.start();		// start the thread	
    }

    // method to process received request message ( part of Ricard-Agrawala algorithm )
    // takes received sequence number, received ID, filename
    public void process_request_message(int their_sn,int j, String filename)
    {
        // boolean flag to identify if our request has higher priority than other requests
        boolean our_priority;

        synchronized(cnode.r_list)
        {
            // update the highest sequence number from this received message
            cnode.r_list.get(filename).cword.high_sn = Math.max(cnode.r_list.get(filename).cword.high_sn,their_sn);
            // set the priority flag
            // our timestamp is earlier or if timestamps are equal and our ID is lower : higher priority
            our_priority = (their_sn > cnode.r_list.get(filename).cword.our_sn) || ( (their_sn == cnode.r_list.get(filename).cword.our_sn) & ( j > cnode.r_list.get(filename).cword.ME ) );
    
            System.out.println("check if need to send reply");
            // if in critical section or we have issued a request with higher priority
            if ( cnode.r_list.get(filename).cword.using || ( cnode.r_list.get(filename).cword.waiting & our_priority) )
            {
                cnode.r_list.get(filename).cword.reply_deferred[j] = true;
                System.out.println("DEFERRING REPLY to "+j+ "for now");
            }
    
            // not in critical section or have no request pending
            if ( !(cnode.r_list.get(filename).cword.using || cnode.r_list.get(filename).cword.waiting) || ( cnode.r_list.get(filename).cword.waiting & !cnode.r_list.get(filename).cword.A[j] & !our_priority ) )
            {
                System.out.println("REPLY to "+ j+";neither in crit nor requesting");
                cnode.r_list.get(filename).cword.A[j] = false;
                crit_reply(filename);
            }
    
            // got a higher priority request
            if ( cnode.r_list.get(filename).cword.waiting & cnode.r_list.get(filename).cword.A[j] & !our_priority )
            {
                System.out.println("REPLY to "+ j+";optimization+received higher priority request");
                // since we are sending a reply, need to unset authorized flag
                cnode.r_list.get(filename).cword.A[j] = false;
                crit_reply(filename);
                crit_request(cnode.r_list.get(filename).cword.our_sn,filename);
            }
        }
    }

    // method to process received reply message ( part of Ricard-Agrawala algorithm )
    // takes received ID, filename
    public void process_reply_message(int j, String filename)
    {
        synchronized(cnode.r_list)
        {
            System.out.println("processing REPLY received from PID "+j);
            // set the authorized flag for corresponding node
            cnode.r_list.get(filename).cword.A[j] = true;
        }
    }
    
    // methods to send setup related messages in the output stream
    public void send_setup()
    {
        out.println("chain_setup");
    }

    public void send_finish()
    {
        out.println("simulation_finish");
    }

    public void send_setup_finish()
    {
        out.println("chain_setup_finish");
    }

    // method to send the REQUEST message with timestamp and file identifier
    public void crit_request(int ts,String filename)
    {
        out.println("REQUEST");
        out.println(ts);
        out.println(my_c_id);
        out.println(filename);
    }

    // method to send the REPLY message with file identifier
    public void crit_reply(String filename)
    {
        out.println("REPLY");
        out.println(my_c_id);
        out.println(filename);
    }

    // method to enquire file to the remote server and update shared files list on client
    public void enquire_files()
    {
        out.println("ENQUIRY");
        String rd_in = null;
        Matcher m_eom = eom.matcher("start");  // initializing the matcher. "start" does not mean anything
        // get filenames till EOM message is received and update the files list
        try
        {
            while(!m_eom.find())
            {
                rd_in = in.readLine();
                m_eom = eom.matcher(rd_in);
                if(!m_eom.find())
                {
                    String filename = rd_in;
                    cnode.files.add(filename);
                } 
                else { break; }  // break out of loop when EOM is received
            }
        }
        catch (IOException e) 
        {
        	System.out.println("Read failed");
        	//System.exit(-1);
        }
    }

    // method to send read_file command
    // print the Content on the console
    public void read_file(String filename)
    {
        out.println("READ");
        out.println(filename);
        try
        {
            String content = null;
            content = in.readLine();
            System.out.println("READ content : "+content);
        }
        catch (IOException e) 
        {
        	System.out.println("Read failed");
        	//System.exit(-1);
        }
    }

    // method to send write_file command
    public void write_file(String filename)
    {
        out.println("WRITE");
        out.println(filename);
        int timestamp = 0;
        synchronized(cnode.r_list)
        {
            timestamp = cnode.r_list.get(filename).cword.our_sn;
        }
        // content = client <ID>, <ts>
        out.println("Client "+my_c_id+", "+timestamp);
        // check if write operation finished on server and then exit method
        try
        {
            String em = null;
            em = in.readLine();
            Matcher m_eom = eom.matcher(em);
            if (m_eom.find())
            {
                System.out.println("WRITE operation finished on server : "+remote_c_id);
            }
            else
            {
                System.out.println("WRITE operation ERROR on server : "+remote_c_id);
            }
        }
        catch (IOException e) 
        {
        	System.out.println("Read failed");
        	//System.exit(-1);
        }
    }

    // method to setup connections to Clients
    public void setup_clients()
    {
        ClientInfo t = new ClientInfo();
        for(int i=0;i<5;i++)
        {
            // for mesh connection between clients
            // initiate connection to clients having ID > current node's ID
            if(i > my_c_id)
            {
                // get client info
                String t_ip = t.hmap.get(i).ip;
                int t_port = Integer.valueOf(t.hmap.get(i).port);
                Thread x = new Thread()
                {
            	public void run()
                    {
                        try
                        {
                            Socket s = new Socket(t_ip,t_port);
                            // SockHandle instance with svr_hdl false and rx_hdl false as this is the socket initiator
                            // and is a connection to another client node
                            SockHandle t = new SockHandle(s,my_ip,my_port,my_c_id,c_list,s_list,false,false,cnode);
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
                x.setName("Client_"+my_c_id+"_SockHandle_to_Client"+i);
                x.start(); 			// start the thread
            }
        }

        // another thread to check until all connections are established ( ie. socket list size =4 )
        // then send a message to my_id+1 client to initiate its connection setup phase
        Thread y = new Thread()
        {
            public void run()
            {
                int size = 0;
                // wait till client connections are setup
                while (size != 4)
                {
                    synchronized(c_list)
                    {
                        size = c_list.size();
                    }
                }
                // if this is not the last client node (ID =4)
                // send chain init message to trigger connection setup
                // phase on the next client
                if(my_c_id != 4)
                {
                    c_list.get(my_c_id+1).send_setup();
                    System.out.println("chain setup init");
                }
                // send the setup finish, from Client 4
                // indicating connection setup phase is complete
                else
                {
                    c_list.get(0).send_setup_finish();
                }
            }
        };
            
        y.setDaemon(true); 	// terminate when main ends
        y.start(); 			// start the thread
    }

    // method to setup connections to servers
    public void setup_servers()
    {
        // get ServerInfo
        ServerInfo t = new ServerInfo();
        // all 3 servers
        for(int i=0;i<3;i++)
        {
            // get the server IP and port info
            String t_ip = t.hmap.get(i).ip;
            int t_port = Integer.valueOf(t.hmap.get(i).port);
            Thread x = new Thread()
            {
                public void run()
                {
                    try
                    {
                        Socket s = new Socket(t_ip,t_port);
                        // SockHandle instance with svr_hdl true and rx_hdl false as this is the socket initiator
                        SockHandle t = new SockHandle(s,my_ip,my_port,my_c_id,c_list,s_list,false,true,cnode);
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
            x.setName("Client_"+my_c_id+"_SockHandle_to_Server"+i);
            x.start(); 			// start the thread
        }
    }

    // method encompasses both server and client connection setup
    public void setup_connections()
    {
        setup_servers();
        setup_clients();
    }

    // method to process incoming commands and data associated with them
    public int rx_cmd(BufferedReader cmd,PrintWriter out)
    {
    	try
    	{
            // get blocked in readLine until something actually comes on the inputStream
            // then perform actions based on the received command
    	    String cmd_in = cmd.readLine();
            // initial_setup sequence to populate the client socket list stored by the client
    	    if(cmd_in.equals("initial_setup"))
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
    	        System.out.println("neighbor connection, PID:"+ Integer.toString(remote_c_id)+ " ip:" + ip + " port = " + port);
                synchronized (c_list)
                {
                    c_list.put(remote_c_id,this);
                }
    	    } 
            // initial_setup_server sequence to populate the server socket list stored by the client
            else if(cmd_in.equals("initial_setup_server"))
            {
                System.out.println("got cmd 1 from server");
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
                // if this is a server handle / connected to the server
                // no need to run this rx_cmd method
                // so return from this method
                if(svr_hdl == true)
                {
                    System.out.println("rx_cmd processing finished");
                    return 0;
                }
            }
            // initiate connection setup once this message is received
            else if(cmd_in.equals("chain_setup"))
            {
    	        System.out.println("chain_setup ");
                setup_connections();
            }
            // perform enquiry and create the Ricart-Agrawala instances after this message
            else if(cmd_in.equals("chain_setup_finish"))
            {
    	        System.out.println("connection setup finished");
                cnode.initiate_enquiry();
                cnode.create_RAlgorithm();
            }
            // to terminate the program
            else if(cmd_in.equals("simulation_finish"))
            {
    	        System.out.println("Finish program execution!");
                cnode.end_program();
                return 0;
            }
            // got a REQUEST message, process it
            else if(cmd_in.equals("REQUEST"))
            {
                int ts = Integer.valueOf(in.readLine());
                int pid = Integer.valueOf(in.readLine());
                String filename = in.readLine();
    	        System.out.println("REQUEST received from PID "+pid+" with timestamp "+ts+" for file "+filename);
                process_request_message(ts,pid,filename);
            }
            // got a REPLY message, process it
            else if(cmd_in.equals("REPLY"))
            {
                int pid = Integer.valueOf(in.readLine());
                String filename = in.readLine();
    	        System.out.println("REPLY received from PID "+pid+" for file "+filename);
                process_reply_message(pid,filename);
            }
    	}
    	catch (IOException e) 
    	{
    	    System.out.println("Read failed");
    	    //System.exit(-1);
    	}
    
    	// default : return 1, to continue processing further commands 
    	return 1;
    }
}
