package aQute.openapi.provider;

import java.io.IOException;
import java.util.List;
import java.util.Map.Entry;
import java.util.concurrent.CopyOnWriteArrayList;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.osgi.service.http.NamespaceException;

public class Dispatcher extends HttpServlet {
	private static final long	serialVersionUID	= 1L;
	final String				prefix;
	final OpenAPIRuntime		runtime;
	final List<OpenAPIBase>		targets				= new CopyOnWriteArrayList<>();

	public Dispatcher(OpenAPIRuntime runtime, String prefix) throws ServletException, NamespaceException {
		this.runtime = runtime;
		this.prefix = prefix;

		assert this.prefix.startsWith("/");

		this.runtime.http.registerServlet(this.prefix, this, null, null);
	}

	public synchronized void add(OpenAPIBase base) {
		this.targets.add(base);
	}

	public void remove(OpenAPIBase base) {
		this.targets.remove(base);
	}

	@Override
	protected void service(HttpServletRequest request, HttpServletResponse response)
			throws ServletException, IOException {
		OpenAPIContext context = new OpenAPIContext(runtime, request, response);
		try {
			runtime.contexts.set(context);
			try {
				String path = request.getPathInfo();

				if (path.endsWith("/"))
					path = path.substring(1, path.length() - 1);
				else
					path = path.substring(1);

				String segments[] = path.split("/");

				for (OpenAPIBase base : targets) {
					context.setTarget(base);
					base.before_(context);
					try {
						if (base.dispatch_(context, segments, 0)) {
							return;
						}
					} finally {
						base.after_(context);
					}
				}

				response.setStatus(HttpServletResponse.SC_NOT_FOUND);

			} finally {
				runtime.contexts.remove();
			}
		} catch (OpenAPIBase.Response e) {
			response.setStatus(e.resultCode);
			for (Entry<String,String> entry : e.headers.entrySet()) {
				response.addHeader(entry.getKey(), entry.getValue());
			}
			Object result = e.getResult();
			context.report(e);
		} catch (SecurityException se) {
			response.setStatus(HttpServletResponse.SC_FORBIDDEN);
		} catch (Exception e) {
			response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
			context.report(e);
		}
	}

	@Override
	public String toString() {
		StringBuilder s = new StringBuilder();
		s.append(prefix);
		s.append(" - ").append(targets);
		return super.toString();
	}
}