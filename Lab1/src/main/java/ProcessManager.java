import org.apache.commons.configuration2.PropertiesConfiguration;
import org.apache.commons.configuration2.ex.ConfigurationException;
import org.apache.log4j.Logger;

import java.io.FileReader;
import java.io.IOException;
import java.net.InetAddress;
import java.net.MalformedURLException;
import java.net.UnknownHostException;
import java.rmi.AlreadyBoundException;
import java.rmi.Naming;
import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.util.ArrayList;
import java.util.List;

public class ProcessManager {

    final static Logger logger = Logger.getLogger(ProcessManager.class);
    private static String prefix = "rmi://";

    public static String[] readConfiguration(){
        // initialize node property
        PropertiesConfiguration config = new PropertiesConfiguration();
        try{
            config.read(new FileReader("url.properties"));
        }catch(IOException e1){
            logger.error("Failed to read configurations. Throw by IOException");
            e1.printStackTrace();
        }catch (ConfigurationException e2){
            logger.error("Failed to read configurations. Throw by ConfigurationException");
            e2.printStackTrace();
        }

        return config.getStringArray("node_url");
    }

    /**
     *
     */
    public static void startServer(int index) {
        String[] urls = readConfiguration();

        try {
            DA_Schiper_Eggli_Sandoz process = new DA_Schiper_Eggli_Sandoz(urls.length, index);
            logger.info("create server at" + urls[index]);
            new Thread(process).start();
            Naming.bind("rmi://localhost/SES", process);

        }catch (RemoteException e1) {
            e1.printStackTrace();
        } catch (AlreadyBoundException e2) {
            e2.printStackTrace();
        } catch (MalformedURLException e3) {
            e3.printStackTrace();
        }
    }


    public static boolean isLocalProcess(String url) {

        String ipaddress = new String();
        try{
            ipaddress = InetAddress.getLocalHost().getHostAddress();
            logger.info("IP " + ipaddress);
        }catch (UnknownHostException e){
            e.printStackTrace();
        }

        if(url.startsWith(prefix + "localhost"))
            return true;
        else if(url.startsWith(prefix + "127.0.0.1"))
            return true;
        else if(url.startsWith(prefix + ipaddress))
            return true;
        else
            return false;
    }
}