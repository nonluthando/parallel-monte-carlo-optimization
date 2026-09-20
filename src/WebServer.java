package MonteCarloMini;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.Executors;

/*
 * Small dependency-free web UI for the Monte Carlo minimisation demo.
 * Serves the static frontend under ./web and exposes POST /api/run,
 * which runs the serial and/or parallel algorithm in-process and
 * returns the results (plus a downsampled terrain render) as JSON.
 *
 * Run with: java -cp bin MonteCarloMini.WebServer [port]
 * (run from the project root so the relative "web" directory resolves)
 */
public class WebServer {

	// hard caps so a request can't be used to exhaust server memory/CPU
	static final int MAX_GRID_DIM = 3000;
	static final double MAX_DENSITY = 2.0;
	static final int MAX_SEARCHES = 2_000_000;
	static final int RENDER_RESOLUTION = 150; // heatmap is always downsampled to at most this many cells per side

	public static void main(String[] args) throws IOException {
		int port = args.length > 0 ? Integer.parseInt(args[0]) : 8080;
		String host = System.getenv().getOrDefault("BIND_HOST", "127.0.0.1");

		HttpServer server = HttpServer.create(new InetSocketAddress(host, port), 0);
		server.createContext("/api/run", new RunHandler());
		server.createContext("/", new StaticHandler());
		server.setExecutor(Executors.newFixedThreadPool(4));
		server.start();
		System.out.println("Monte Carlo optimisation UI running at http://" + host + ":" + port + "/");
	}

	// ---- static file serving ----

	static class StaticHandler implements HttpHandler {
		public void handle(HttpExchange exchange) throws IOException {
			if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
				exchange.sendResponseHeaders(405, -1);
				return;
			}
			String path = exchange.getRequestURI().getPath();
			if (path.equals("/")) path = "/index.html";
			// prevent path traversal outside the web/ directory
			File webRoot = new File("web").getCanonicalFile();
			File file = new File(webRoot, path).getCanonicalFile();
			if (!file.getPath().startsWith(webRoot.getPath()) || !file.isFile()) {
				byte[] body = "Not found".getBytes(StandardCharsets.UTF_8);
				exchange.sendResponseHeaders(404, body.length);
				try (OutputStream os = exchange.getResponseBody()) { os.write(body); }
				return;
			}
			exchange.getResponseHeaders().set("Content-Type", contentType(file.getName()));
			byte[] body = readFile(file);
			exchange.sendResponseHeaders(200, body.length);
			try (OutputStream os = exchange.getResponseBody()) { os.write(body); }
		}

		String contentType(String name) {
			if (name.endsWith(".html")) return "text/html; charset=utf-8";
			if (name.endsWith(".css")) return "text/css; charset=utf-8";
			if (name.endsWith(".js")) return "application/javascript; charset=utf-8";
			return "application/octet-stream";
		}

		byte[] readFile(File file) throws IOException {
			ByteArrayOutputStream out = new ByteArrayOutputStream();
			try (InputStream in = new FileInputStream(file)) {
				byte[] buf = new byte[8192];
				int n;
				while ((n = in.read(buf)) != -1) out.write(buf, 0, n);
			}
			return out.toByteArray();
		}
	}

	// ---- /api/run ----

	static class RunHandler implements HttpHandler {
		public void handle(HttpExchange exchange) throws IOException {
			if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
				exchange.sendResponseHeaders(405, -1);
				return;
			}
			try {
				Map<String, String> params = parseForm(exchange.getRequestBody());
				String json = runRequest(params);
				byte[] body = json.getBytes(StandardCharsets.UTF_8);
				exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
				exchange.sendResponseHeaders(200, body.length);
				try (OutputStream os = exchange.getResponseBody()) { os.write(body); }
			} catch (IllegalArgumentException e) {
				byte[] body = ("{\"error\":" + jsonString(e.getMessage()) + "}").getBytes(StandardCharsets.UTF_8);
				exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
				exchange.sendResponseHeaders(400, body.length);
				try (OutputStream os = exchange.getResponseBody()) { os.write(body); }
			} catch (Exception e) {
				byte[] body = ("{\"error\":" + jsonString("internal error: " + e.getMessage()) + "}").getBytes(StandardCharsets.UTF_8);
				exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
				exchange.sendResponseHeaders(500, body.length);
				try (OutputStream os = exchange.getResponseBody()) { os.write(body); }
			}
		}
	}

	static String runRequest(Map<String, String> params) {
		int rows = intParam(params, "rows", 1, MAX_GRID_DIM);
		int cols = intParam(params, "cols", 1, MAX_GRID_DIM);
		double xmin = doubleParam(params, "xmin");
		double xmax = doubleParam(params, "xmax");
		double ymin = doubleParam(params, "ymin");
		double ymax = doubleParam(params, "ymax");
		double density = doubleParam(params, "density", 0.0001, MAX_DENSITY);
		int threshold = intParam(params, "threshold", 1, 1_000_000);
		String mode = params.getOrDefault("mode", "both");

		if (xmax <= xmin) throw new IllegalArgumentException("xmax must be greater than xmin");
		if (ymax <= ymin) throw new IllegalArgumentException("ymax must be greater than ymin");

		long numSearches = (long) (rows * (double) cols * density);
		if (numSearches < 1) throw new IllegalArgumentException("search density too low - no searches would run");
		if (numSearches > MAX_SEARCHES) throw new IllegalArgumentException("rows * cols * density too large (max " + MAX_SEARCHES + " searches)");

		long seed = System.nanoTime();

		RunResult parallelResult = null;
		RunResult serialResult = null;

		if (mode.equals("parallel") || mode.equals("both")) {
			parallelResult = runParallel(rows, cols, xmin, xmax, ymin, ymax, density, threshold, seed);
		}
		if (mode.equals("serial") || mode.equals("both")) {
			serialResult = runSerial(rows, cols, xmin, xmax, ymin, ymax, density, seed);
		}

		// render the landscape (independent of algorithm grid resolution) and, for whichever
		// run we have, a downsampled view of which cells the searches actually visited
		RunResult forRender = parallelResult != null ? parallelResult : serialResult;

		StringBuilder json = new StringBuilder();
		json.append("{");
		json.append("\"rows\":").append(rows).append(",");
		json.append("\"cols\":").append(cols).append(",");
		json.append("\"numSearches\":").append(numSearches).append(",");
		json.append("\"heights\":").append(renderHeights(xmin, xmax, ymin, ymax)).append(",");
		json.append("\"visited\":").append(renderVisited(forRender.terrain, rows, cols)).append(",");
		if (serialResult != null) {
			json.append("\"serial\":").append(resultJson(serialResult)).append(",");
		}
		if (parallelResult != null) {
			json.append("\"parallel\":").append(resultJson(parallelResult)).append(",");
		}
		if (serialResult != null && parallelResult != null && parallelResult.timeMs > 0) {
			double speedup = serialResult.timeMs / (double) Math.max(parallelResult.timeMs, 1);
			json.append("\"speedup\":").append(String.format("%.3f", speedup)).append(",");
		}
		if (json.charAt(json.length() - 1) == ',') json.setLength(json.length() - 1);
		json.append("}");
		return json.toString();
	}

	static class RunResult {
		long timeMs;
		int globalMin;
		double minX, minY;
		int visited, evaluated;
		TerrainArea terrain;
	}

	static RunResult runSerial(int rows, int cols, double xmin, double xmax, double ymin, double ymax, double density, long seed) {
		TerrainArea terrain = new TerrainArea(rows, cols, xmin, xmax, ymin, ymax);
		Random rand = new Random(seed);
		int numSearches = (int) (rows * cols * density);
		Search[] searches = new Search[numSearches];
		for (int i = 0; i < numSearches; i++) {
			searches[i] = new Search(i + 1, rand.nextInt(rows), rand.nextInt(cols), terrain);
		}
		long start = System.currentTimeMillis();
		int min = Integer.MAX_VALUE;
		int finder = -1;
		for (int i = 0; i < numSearches; i++) {
			int localMin = searches[i].find_valleys();
			if (!searches[i].isStopped() && localMin < min) {
				min = localMin;
				finder = i;
			}
		}
		long end = System.currentTimeMillis();

		RunResult r = new RunResult();
		r.timeMs = end - start;
		r.globalMin = min;
		r.minX = finder >= 0 ? terrain.getXcoord(searches[finder].getPos_row()) : 0;
		r.minY = finder >= 0 ? terrain.getYcoord(searches[finder].getPos_col()) : 0;
		r.visited = terrain.getGrid_points_visited();
		r.evaluated = terrain.getGrid_points_evaluated();
		r.terrain = terrain;
		return r;
	}

	static RunResult runParallel(int rows, int cols, double xmin, double xmax, double ymin, double ymax, double density, int threshold, long seed) {
		TerrainArea terrain = new TerrainArea(rows, cols, xmin, xmax, ymin, ymax);
		Random rand = new Random(seed);
		int numSearches = (int) (rows * cols * density);
		SearchParallel[] searches = new SearchParallel[numSearches];
		for (int i = 0; i < numSearches; i++) {
			searches[i] = new SearchParallel(i + 1, rand.nextInt(rows), rand.nextInt(cols), terrain);
		}
		ForkJoinPool pool = new ForkJoinPool();
		long start = System.currentTimeMillis();
		ParallelMinTask task = new ParallelMinTask(0, numSearches, searches, threshold);
		int[] result = pool.invoke(task);
		long end = System.currentTimeMillis();

		RunResult r = new RunResult();
		r.timeMs = end - start;
		r.globalMin = result[0];
		int finder = result[1];
		r.minX = finder >= 0 ? terrain.getXcoord(searches[finder].getPos_row()) : 0;
		r.minY = finder >= 0 ? terrain.getYcoord(searches[finder].getPos_col()) : 0;
		r.visited = terrain.getGrid_points_visited();
		r.evaluated = terrain.getGrid_points_evaluated();
		r.terrain = terrain;
		return r;
	}

	// Fork/Join task mirroring MonteCarloMinimizationParallel, but also tracking which
	// search found the global minimum so the UI can mark its location on the map.
	static class ParallelMinTask extends java.util.concurrent.RecursiveTask<int[]> {
		final int lo, hi, threshold;
		final SearchParallel[] searches;

		ParallelMinTask(int lo, int hi, SearchParallel[] searches, int threshold) {
			this.lo = lo;
			this.hi = hi;
			this.searches = searches;
			this.threshold = threshold;
		}

		protected int[] compute() {
			if (hi - lo <= threshold) {
				int min = Integer.MAX_VALUE;
				int finder = -1;
				for (int i = lo; i < hi; i++) {
					int localMin = searches[i].find_valleys();
					if (!searches[i].isStopped() && localMin < min) {
						min = localMin;
						finder = i;
					}
				}
				return new int[]{min, finder};
			} else {
				int mid = (lo + hi) / 2;
				ParallelMinTask left = new ParallelMinTask(lo, mid, searches, threshold);
				ParallelMinTask right = new ParallelMinTask(mid, hi, searches, threshold);
				left.fork();
				int[] rightResult = right.compute();
				int[] leftResult = left.join();
				return leftResult[0] <= rightResult[0] ? leftResult : rightResult;
			}
		}
	}

	static String resultJson(RunResult r) {
		StringBuilder sb = new StringBuilder();
		sb.append("{");
		sb.append("\"timeMs\":").append(r.timeMs).append(",");
		sb.append("\"globalMin\":").append(r.globalMin).append(",");
		sb.append("\"minX\":").append(String.format("%.4f", r.minX)).append(",");
		sb.append("\"minY\":").append(String.format("%.4f", r.minY)).append(",");
		sb.append("\"visited\":").append(r.visited).append(",");
		sb.append("\"evaluated\":").append(r.evaluated);
		sb.append("}");
		return sb.toString();
	}

	// downsampled height map of the underlying function, independent of the algorithm's own grid size
	static String renderHeights(double xmin, double xmax, double ymin, double ymax) {
		int res = RENDER_RESOLUTION;
		StringBuilder sb = new StringBuilder();
		sb.append("{\"res\":").append(res).append(",\"values\":[");
		for (int i = 0; i < res; i++) {
			if (i > 0) sb.append(",");
			sb.append("[");
			double x = xmin + (xmax - xmin) * i / (res - 1);
			for (int j = 0; j < res; j++) {
				if (j > 0) sb.append(",");
				double y = ymin + (ymax - ymin) * j / (res - 1);
				sb.append(String.format("%.4f", TerrainArea.computeValue(x, y)));
			}
			sb.append("]");
		}
		sb.append("]}");
		return sb.toString();
	}

	// downsampled view of which grid cells a search actually visited
	static String renderVisited(TerrainArea terrain, int rows, int cols) {
		int res = RENDER_RESOLUTION;
		int strideR = Math.max(1, (int) Math.ceil(rows / (double) res));
		int strideC = Math.max(1, (int) Math.ceil(cols / (double) res));
		int outR = (rows + strideR - 1) / strideR;
		int outC = (cols + strideC - 1) / strideC;

		StringBuilder sb = new StringBuilder();
		sb.append("{\"rows\":").append(outR).append(",\"cols\":").append(outC).append(",\"values\":[");
		for (int i = 0; i < outR; i++) {
			if (i > 0) sb.append(",");
			sb.append("[");
			int gridI = Math.min(i * strideR, rows - 1);
			for (int j = 0; j < outC; j++) {
				if (j > 0) sb.append(",");
				int gridJ = Math.min(j * strideC, cols - 1);
				sb.append(terrain.getVisited(gridI, gridJ) != 0 ? 1 : 0);
			}
			sb.append("]");
		}
		sb.append("]}");
		return sb.toString();
	}

	// ---- helpers ----

	static Map<String, String> parseForm(InputStream body) throws IOException {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		byte[] buf = new byte[4096];
		int n;
		int total = 0;
		while ((n = body.read(buf)) != -1) {
			total += n;
			if (total > 1_000_000) throw new IllegalArgumentException("request body too large");
			out.write(buf, 0, n);
		}
		String raw = out.toString(StandardCharsets.UTF_8);
		Map<String, String> params = new HashMap<>();
		for (String pair : raw.split("&")) {
			if (pair.isEmpty()) continue;
			int eq = pair.indexOf('=');
			String key = eq >= 0 ? pair.substring(0, eq) : pair;
			String value = eq >= 0 ? pair.substring(eq + 1) : "";
			params.put(URLDecoder.decode(key, "UTF-8"), URLDecoder.decode(value, "UTF-8"));
		}
		return params;
	}

	static int intParam(Map<String, String> params, String name, int min, int max) {
		String v = params.get(name);
		if (v == null || v.isEmpty()) throw new IllegalArgumentException("missing parameter: " + name);
		int value;
		try {
			value = Integer.parseInt(v.trim());
		} catch (NumberFormatException e) {
			throw new IllegalArgumentException("invalid integer for " + name);
		}
		if (value < min || value > max) throw new IllegalArgumentException(name + " must be between " + min + " and " + max);
		return value;
	}

	static double doubleParam(Map<String, String> params, String name) {
		String v = params.get(name);
		if (v == null || v.isEmpty()) throw new IllegalArgumentException("missing parameter: " + name);
		try {
			return Double.parseDouble(v.trim());
		} catch (NumberFormatException e) {
			throw new IllegalArgumentException("invalid number for " + name);
		}
	}

	static double doubleParam(Map<String, String> params, String name, double min, double max) {
		double value = doubleParam(params, name);
		if (value < min || value > max) throw new IllegalArgumentException(name + " must be between " + min + " and " + max);
		return value;
	}

	static String jsonString(String s) {
		return "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
	}
}
