package aQute.openapi.provider;

import java.net.URI;
import java.net.URL;

import javax.servlet.Servlet;
import javax.servlet.ServletException;

import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.junit.rules.TestRule;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;
import org.osgi.service.http.NamespaceException;

import aQute.bnd.http.HttpClient;
import aQute.openapi.provider.OpenAPIBase;
import aQute.openapi.provider.OpenAPIRuntime;
import aQute.openapi.provider.SecurityProviderManager;

public class OpenAPIServerTestRule implements TestRule {
	public URI						uri;
	public Server					server	= new Server(0);
	public ServletContextHandler	handler;
	public OpenAPIRuntime			runtime	= new OpenAPIRuntime() {
												@Override
												public java.io.Closeable registerServlet(String alias, Servlet servlet)
														throws ServletException, NamespaceException {
													handler.addServlet(new ServletHolder(servlet), (alias + "/*"));
													return () -> {
																							};
												};
											};
	public SecurityProviderManager	securityProviderManager		= new SecurityProviderManager();
	public HttpClient				http	= new HttpClient();

	@Override
	public Statement apply(Statement statement, Description description) {

		return new Statement() {

			@Override
			public void evaluate() throws Throwable {
				try {
					handler = new ServletContextHandler(
							ServletContextHandler.SESSIONS);
					handler.setContextPath("/");
					handler.addServlet(new ServletHolder(securityProviderManager),
							SecurityProviderManager.PATTERN);
					server.setHandler(handler);
					server.start();

					while (!(server.isStarted() || server.isRunning()))
						Thread.sleep(100);

					uri = server.getURI();
					statement.evaluate();
				} finally {
					server.stop();
					server.join();
				}
			}
		};
	}

	public <X extends OpenAPIBase> X add(X x) throws Exception {
		runtime.add(x);
		return x;

	}

	public String put(String path, String payload) throws Exception {
		URL uri = this.uri.resolve(path).toURL();
		return http.build().put().upload(payload.replace('\'', '"')).get(String.class).go(uri).replace('"', '\'');
	}
}
