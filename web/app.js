(function () {
	const form = document.getElementById('run-form');
	const statusEl = document.getElementById('status');
	const runBtn = document.getElementById('run-btn');
	const resultsPanel = document.getElementById('results-panel');
	const resultsBody = document.querySelector('#results-table tbody');
	const speedupLine = document.getElementById('speedup-line');
	const canvas = document.getElementById('canvas');
	const ctx = canvas.getContext('2d');

	form.addEventListener('submit', function (e) {
		e.preventDefault();
		runOptimisation();
	});

	function runOptimisation() {
		const data = new FormData(form);
		const params = new URLSearchParams();
		for (const [key, value] of data.entries()) params.append(key, value);

		setStatus('Running...', false);
		runBtn.disabled = true;

		fetch('/api/run', {
			method: 'POST',
			headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
			body: params.toString(),
		})
			.then(async (res) => {
				const body = await res.json();
				if (!res.ok) throw new Error(body.error || ('HTTP ' + res.status));
				return body;
			})
			.then((body) => {
				setStatus('Done.', false);
				renderResults(body);
				renderCanvas(body, {
					xmin: parseFloat(data.get('xmin')),
					xmax: parseFloat(data.get('xmax')),
					ymin: parseFloat(data.get('ymin')),
					ymax: parseFloat(data.get('ymax')),
				});
			})
			.catch((err) => {
				setStatus(err.message, true);
			})
			.finally(() => {
				runBtn.disabled = false;
			});
	}

	function setStatus(text, isError) {
		statusEl.textContent = text;
		statusEl.classList.toggle('error', !!isError);
	}

	function renderResults(body) {
		resultsBody.innerHTML = '';
		let rows = [];
		if (body.serial) rows.push(['Serial', body.serial]);
		if (body.parallel) rows.push(['Parallel', body.parallel]);

		for (const [label, r] of rows) {
			const tr = document.createElement('tr');
			tr.innerHTML =
				'<td>' + label + '</td>' +
				'<td>' + r.timeMs + '</td>' +
				'<td>' + r.globalMin + '</td>' +
				'<td>' + r.minX.toFixed(3) + '</td>' +
				'<td>' + r.minY.toFixed(3) + '</td>' +
				'<td>' + r.visited + '</td>' +
				'<td>' + r.evaluated + '</td>';
			resultsBody.appendChild(tr);
		}

		if (body.speedup) {
			speedupLine.hidden = false;
			speedupLine.textContent = 'Parallel speedup: ' + body.speedup + '× (' +
				body.numSearches + ' searches across ' + body.rows + '×' + body.cols + ' grid)';
		} else {
			speedupLine.hidden = true;
		}

		resultsPanel.hidden = false;
	}

	function renderCanvas(body, bounds) {
		const w = canvas.width, h = canvas.height;
		ctx.clearRect(0, 0, w, h);

		// --- height heatmap (independent resolution, spans full data bounds) ---
		const heights = body.heights;
		const res = heights.res;
		let min = Infinity, max = -Infinity;
		for (let i = 0; i < res; i++) {
			for (let j = 0; j < res; j++) {
				const v = heights.values[i][j];
				if (v < min) min = v;
				if (v > max) max = v;
			}
		}
		const range = (max - min) || 1;

		const cellW = w / res, cellH = h / res;
		for (let i = 0; i < res; i++) {
			for (let j = 0; j < res; j++) {
				const v = heights.values[i][j];
				const t = (v - min) / range;
				ctx.fillStyle = heatColor(t);
				const px = i * cellW;
				// flip vertically so y increases upward
				const py = h - (j + 1) * cellH;
				ctx.fillRect(px, py, cellW + 0.5, cellH + 0.5);
			}
		}

		// --- visited overlay ---
		const visited = body.visited;
		ctx.fillStyle = 'rgba(255,255,255,0.55)';
		const vCellW = w / visited.cols, vCellH = h / visited.rows;
		for (let i = 0; i < visited.rows; i++) {
			for (let j = 0; j < visited.cols; j++) {
				if (visited.values[i][j]) {
					const px = (i / visited.rows) * w;
					const py = h - ((j + 1) / visited.cols) * h;
					ctx.fillRect(px, py, Math.max(vCellW, 1), Math.max(vCellH, 1));
				}
			}
		}

		// --- global minimum marker(s) ---
		const markers = [];
		if (body.parallel) markers.push({ x: body.parallel.minX, y: body.parallel.minY, color: '#ff3b3b' });
		if (body.serial && !body.parallel) markers.push({ x: body.serial.minX, y: body.serial.minY, color: '#ff3b3b' });

		for (const m of markers) {
			const px = ((m.x - bounds.xmin) / (bounds.xmax - bounds.xmin)) * w;
			const py = h - ((m.y - bounds.ymin) / (bounds.ymax - bounds.ymin)) * h;
			drawCross(px, py, m.color);
		}
	}

	function drawCross(x, y, color) {
		ctx.strokeStyle = color;
		ctx.lineWidth = 2;
		ctx.beginPath();
		ctx.arc(x, y, 7, 0, Math.PI * 2);
		ctx.moveTo(x - 10, y);
		ctx.lineTo(x + 10, y);
		ctx.moveTo(x, y - 10);
		ctx.lineTo(x, y + 10);
		ctx.stroke();
	}

	// simple dark-to-light heat gradient (blue -> teal -> yellow -> red)
	function heatColor(t) {
		t = Math.max(0, Math.min(1, t));
		const stops = [
			[0.00, [13, 20, 60]],
			[0.25, [30, 90, 160]],
			[0.5, [40, 170, 140]],
			[0.75, [230, 200, 60]],
			[1.00, [230, 60, 40]],
		];
		for (let i = 0; i < stops.length - 1; i++) {
			const [t0, c0] = stops[i];
			const [t1, c1] = stops[i + 1];
			if (t >= t0 && t <= t1) {
				const f = (t - t0) / (t1 - t0 || 1);
				const r = Math.round(c0[0] + (c1[0] - c0[0]) * f);
				const g = Math.round(c0[1] + (c1[1] - c0[1]) * f);
				const b = Math.round(c0[2] + (c1[2] - c0[2]) * f);
				return 'rgb(' + r + ',' + g + ',' + b + ')';
			}
		}
		return 'rgb(230,60,40)';
	}
})();
