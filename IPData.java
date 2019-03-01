import java.io.*;
// Data structure to store neighbor information 
public class IPData implements Serializable 
{
    public String ip;
    public String port;
    IPData(String ip,String port)
    {
        this.ip = ip;
        this.port = port;
    }
    
    // override default equals method which is used by the contains/remove methods to check equality of ip and port since this will be unique
    public boolean equals(Object t) 
    {
        try 
        {
            return (ip.equals(((IPData)t).ip)) && (port.equals(((IPData)t).port));
        } 
        catch (ClassCastException e)
        {
            return false; 
        }
    }
}
