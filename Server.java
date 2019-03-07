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
class ServerNode
{
    // server socket variables 
    ServerSocket server = null; 	// server socket used by PeerWorker, socket for maintaining the P2P network
    final int local_d = 1;
    // table that maintains list of files
    //List<GLBInfo> world_content	= new LinkedList<GLBInfo>();
    // table that maintains neighbors of this node 
    //List<IPData> neighbors	= new LinkedList<IPData>();
    HashMap<Integer, ServerSockHandle> s_list = new HashMap<Integer, ServerSockHandle>();
    ServerNode snode = null;
    // variables to obtain IP/port information of where the node is running
    InetAddress myip = null;
    String hostname = null;
    String ip = null;
    String port = null;
    int c_id = -1;
    RAlgorithm ra_inst = null;
    ServerInfo s_info = null;
    List<String> files = new ArrayList<String>();
    ServerNode(int c_id)
    {
        this.s_info = new ServerInfo();
        this.c_id = c_id;
    	this.port = s_info.hmap.get(c_id).port;
        this.populate_files();
        this.listenSocket();
        this.snode = this;
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
    		}
                else if(m_LIST.find())
                { 
		    System.out.println("Files:");
		    for (int i = 0; i < files.size(); i++) {
		    	System.out.println(files.get(i));
		    }
                    synchronized (s_list)
                    {
                        System.out.println("\n=== Connections to clients ===");
                        s_list.keySet().forEach(key -> {
                        System.out.println("key:"+key + " => ID " + s_list.get(key).remote_c_id);
                        });
                        System.out.println("=== size ="+s_list.size());
                    }
    		}
                else if(m_START.find())
                { 
    		}
                else if(m_FINISH.find())
                { 
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
    		System.out.println("Enter commands: LIST");
    		Scanner input = new Scanner(System.in);
    		while(rx_cmd(input) != 0) { }  // to loop forever
    	}
    }
    public String do_read_operation(String filename)
    {
        File file = new File("./"+c_id+"/"+filename);
	if (!file.exists()) 
        {
	    System.out.println("File "+filename+" does not exist");
            return "NULL";
	}
        return readFromLast(file);
    }
    public String readFromLast(File file)
    {
        int lines = 0;
        StringBuilder builder = new StringBuilder();
        RandomAccessFile randomAccessFile = null;
        try 
        {
            randomAccessFile = new RandomAccessFile(file, "r");
            long fileLength = file.length() - 1;
            // Set the pointer at the last of the file
            randomAccessFile.seek(fileLength);
            for(long pointer = fileLength; pointer >= 0; pointer--)
            {
                randomAccessFile.seek(pointer);
                char c;
                // read from the last one char at the time
                c = (char)randomAccessFile.read(); 
                // break when end of the line
                if(c == '\n')
                {
                    break;
                }
                builder.append(c);
            }
            // Since line is read from the last so it 
            // is in reverse so use reverse method to make it right
            builder.reverse();
            System.out.println("Line - " + builder.toString());
        } 
        catch (FileNotFoundException e) 
        {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        catch (IOException e) 
        {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        finally
        {
            if(randomAccessFile != null)
            {
                try 
                {
                    randomAccessFile.close();
                } 
                catch (IOException e) 
                {
                    // TODO Auto-generated catch block
                    e.printStackTrace();
                }
            }
        }
        return builder.toString();
    }
    public void do_write_operation(String filename,String content)
    {
        File file = new File("./"+c_id+"/"+filename);
	if (!file.exists()) 
        {
	    System.out.println("File "+filename+" does not exist");
            return;
	}
        try
        {
            FileWriter fw = new FileWriter(file, true);
            fw.write("\n"+content);
            fw.close();
        }
        catch (FileNotFoundException e) 
        {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        catch (IOException e) 
        {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }

    public void populate_files()
    {
        File folder = new File("./"+c_id+"/");
        File[] listOfFiles = folder.listFiles();
        for (int i = 0; i < listOfFiles.length; i++) 
        {
            if (listOfFiles[i].isFile()) 
            {
                System.out.println("File " + listOfFiles[i].getName());
                files.add(listOfFiles[i].getName());
                clearTheFile(listOfFiles[i].getName());
            } 
            else if (listOfFiles[i].isDirectory()) 
            {
                System.out.println("Directory " + listOfFiles[i].getName());
            }
        }

    }
    public void clearTheFile(String filename) {
        try
        {
            FileWriter fwOb = new FileWriter("./"+c_id+"/"+filename, false); 
            PrintWriter pwOb = new PrintWriter(fwOb, false);
            pwOb.flush();
            pwOb.close();
            fwOb.close();
        }
        catch (FileNotFoundException e) 
        {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        catch (IOException e) 
        {
            // TODO Auto-generated catch block
            e.printStackTrace();
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

    // method to start server and listen for incoming connections
    public void listenSocket()
    {
        // create server socket on specified port number
        try
        {
            // create server socket and display host/addressing information of this node
            server = new ServerSocket(Integer.valueOf(port)); 
            System.out.println("ServerNode running on port " + port +"," + " use ctrl-C to end");
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
                	ServerSockHandle t = new ServerSockHandle(s,ip,port,c_id,s_list,true,snode);
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
    	if (args.length != 1)
    	{
    	    System.out.println("Usage: java ServerNode <server-id>");
    	    System.exit(1);
    	}
    	ServerNode server = new ServerNode(Integer.valueOf(args[0]));
    	//server.listenSocket();
    }
}
