import getfavicon.Servlet;
import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.handler.HandlerList;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import util.Config;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * And390 - 28.07.2017
 */
public class Main
{
    private static Logger log = LoggerFactory.getLogger(Main.class);

    private static <T> T add(Collection<? super T> collection, T elem)  {  collection.add(elem);  return elem;  }

    public static void main(String[] args)
    {
        //List<AutoCloseable> closeables = new ArrayList<>();

        try  {

//            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
//                for (AutoCloseable closeable : closeables)
//                try  {  closeable.close();  }
//                catch (Throwable e)  {  log.error("error on close {}", closeable, e);  }
//                log.info("Server stopped.");
//            }));

            //  initialize server
            int port = Config.getInt("server.port", 1, 65535);
            Server jettyServer = new Server(port);

            ServletContextHandler servletHandler = new ServletContextHandler(ServletContextHandler.SESSIONS);
            servletHandler.setResourceBase("web/");
            servletHandler.setContextPath("/");
            servletHandler.addServlet(new ServletHolder(Servlet.class), "/*");

            HandlerList handlers = new HandlerList();
            handlers.setHandlers(new Handler[] { servletHandler });
            jettyServer.setHandler(handlers);

            try {
                jettyServer.start();
                log.info("Server started on port " + port);
                jettyServer.join();
            } finally {
                jettyServer.destroy();
            }

        }
        catch (Throwable e)  {
            log.error("", e);
        }
    }
}
