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

// SockHandle class 
class SockHandle
{
	PrintWriter out = null; 	// to send data from server to the client
	BufferedReader in = null; 	// to read data coming to server from client
	Socket client;      // socket instance for the connecting client
        final int local_d = 1;
	String ip = null; 			// remote ip address
	String port = null; 		// remote port 
	String my_ip = null; 		// ip of this node
	String my_port = null; 		// port of this node
        int my_c_id = -1;
        int remote_c_id = -1;
        HashMap<Integer, SockHandle> c_list = null;
        HashMap<Integer, SockHandle> s_list = null;
        boolean rx_hdl = false;
        boolean svr_hdl = false;
        boolean defer_reply = false;
        Boolean[] optimized_reply = null;
        ClientNode cnode = null;

	public static Pattern eom = Pattern.compile("^EOM");  // generic 'end of message' pattern 

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
                this.optimized_reply = optimized_reply;
                this.rx_hdl = rx_hdl;
                this.svr_hdl = svr_hdl;
                this.cnode = cnode;
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
                        synchronized (c_list)
                        {
                            c_list.put(remote_c_id,this);
                        }
                    }
		    System.out.println("try svr_hdl "+svr_hdl);
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
		Thread read = new Thread()
                {
			public void run()
                        {
			    while(rx_cmd(in,out) != 0) { }
                        }
		};
		read.setDaemon(true); 	// terminate when main ends
                read.start();		// start the thread	
		// run and start thread to process incoming commands which is part of the custom protocol to handle PUBLISH and UNPUBLISH messages
	}
        public void process_request_message(int their_sn,int j)
        {
            boolean our_priority;
            synchronized(cnode.ra_inst.cword){
            cnode.ra_inst.cword.high_sn = Math.max(cnode.ra_inst.cword.high_sn,their_sn);
            our_priority = (their_sn > cnode.ra_inst.cword.our_sn) || ( (their_sn == cnode.ra_inst.cword.our_sn) & ( j > cnode.ra_inst.cword.ME ) );

            System.out.println("check if need to send reply");
            if ( cnode.ra_inst.cword.using || ( cnode.ra_inst.cword.waiting & our_priority) )
            {
                cnode.ra_inst.cword.reply_deferred[j] = true;
                System.out.println("DEFERRING REPLY to "+j+ "for now");
            }

            if ( !(cnode.ra_inst.cword.using || cnode.ra_inst.cword.waiting) || ( cnode.ra_inst.cword.waiting & !cnode.ra_inst.cword.A[j] & !our_priority ) )
            {
                System.out.println("REPLY to "+ j+";neither in crit nor requesting");
                cnode.ra_inst.cword.A[j] = false;
                crit_reply();
            }

            if ( cnode.ra_inst.cword.waiting & cnode.ra_inst.cword.A[j] & !our_priority )
            {
                System.out.println("REPLY to "+ j+";optimization+received higher priority request");
                cnode.ra_inst.cword.A[j] = false;
                crit_reply();
                crit_request(cnode.ra_inst.cword.our_sn);
            }
            }

        }
        public void process_reply_message(int j)
        {
            synchronized(cnode.ra_inst.cword){
            System.out.println("processing REPLY received from PID "+j);
            cnode.ra_inst.cword.A[j] =true;
            }
        }

        public void send_setup()
        {
            out.println("chain_setup");
        }
        public void send_setup_finish()
        {
            out.println("chain_setup_finish");
        }
        public void crit_request(int ts)
        {
            out.println("REQUEST");
            out.println(ts);
            out.println(my_c_id);
        }
        public void crit_reply()
        {
            out.println("REPLY");
            //out.println(ts);
            out.println(my_c_id);
        }
        public void enquire_files()
        {
            out.println("ENQUIRY");
	    String rd_in = null;
	    Matcher m_eom = eom.matcher("start");  // initializing the matcher. "start" does not mean anything
	    // obtain metadata from server till EOM is received 
	    System.out.println("before while loop ");
            try
            {
	        while(!m_eom.find())
                {
	            System.out.println("in while loop ");
	            rd_in = in.readLine();
	            m_eom = eom.matcher(rd_in);
	            System.out.println("got "+rd_in);
	            if(!m_eom.find())
                    {
	                // add metadata to respective list
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
        public void write_file(String filename,String content)
        {
            out.println("READ");
            out.println(filename);
            out.println(content);
            try
            {
                String em = null;
                em = in.readLine();
                if (em == "EOM")
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
        public void setup_clients()
        {
            ClientInfo t = new ClientInfo();
            for(int i=0;i<5;i++)
            {
                if(i > my_c_id)
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
                    x.start(); 			// start the thread

                }
            }
            Thread y = new Thread()
            {
                public void run()
                {
                    int size = 0;
                    while (size != 4)
                    {
                        synchronized(c_list)
                        {
                            size = c_list.size();
                        }
                    }
                    if(my_c_id != 4)
                    {
                        c_list.get(my_c_id+1).send_setup();
	                System.out.println("chain setup init");
                    }
                    else
                    {
                        c_list.get(0).send_setup_finish();
                    }
                }
            };
                
            y.setDaemon(true); 	// terminate when main ends
            y.start(); 			// start the thread
        }
        public void setup_servers()
        {
            ServerInfo t = new ServerInfo();
            for(int i=0;i<3;i++)
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
                x.start(); 			// start the thread
            }
        }
        public void setup_connections()
        {
            setup_servers();
            setup_clients();
        }
	// method to process incoming commands and data associated with them
	public int rx_cmd(BufferedReader cmd,PrintWriter out){
		try
		{
		    String cmd_in = cmd.readLine();
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
			        //out.println("EOM");
			} 
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
			        System.out.println("svr_hdl "+svr_hdl);
                                if(svr_hdl == true)
                                {
			            System.out.println("rx_cmd processing finished");
                                    return 0;
                                }
			        //out.println("EOM");
			}
                        else if(cmd_in.equals("chain_setup"))
                        {
                            setup_connections();
                        }
                        else if(cmd_in.equals("chain_setup_finish"))
                        {
			    System.out.println("connection setup finished");
                            cnode.create_RAlgorithm();
                            cnode.initiate_enquiry();
                        }
                        else if(cmd_in.equals("REQUEST"))
                        {
                            int ts = Integer.valueOf(in.readLine());
                            int pid = Integer.valueOf(in.readLine());
			    System.out.println("REQUEST received from PID "+pid+" with timestamp "+ts);
                            process_request_message(ts,pid);
                        }
                        else if(cmd_in.equals("REPLY"))
                        {
                            //int ts = Integer.valueOf(in.readLine());
                            int pid = Integer.valueOf(in.readLine());
			    System.out.println("REPLY received from PID "+pid);
                            process_reply_message(pid);
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
