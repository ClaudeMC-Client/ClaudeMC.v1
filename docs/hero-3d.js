/* ClaudeMC – Three.js hero background (floating Minecraft-style cubes) */
(function () {
  const hero = document.querySelector('.hero');
  if (!hero) return;

  /* ── Canvas setup ─────────────────────────────────────────────────── */
  const canvas = document.createElement('canvas');
  canvas.id = 'hero-canvas';
  Object.assign(canvas.style, {
    position: 'absolute', inset: '0',
    width: '100%', height: '100%',
    pointerEvents: 'none', zIndex: '0',
  });
  hero.prepend(canvas);

  /* make hero children sit above canvas */
  hero.querySelectorAll(':scope > *:not(#hero-canvas)').forEach(el => {
    el.style.position = 'relative';
    el.style.zIndex   = '1';
  });

  /* ── Three.js scene ───────────────────────────────────────────────── */
  const THREE = window.THREE;
  if (!THREE) return;

  const W = canvas.clientWidth  || hero.clientWidth;
  const H = canvas.clientHeight || hero.clientHeight || 520;

  const renderer = new THREE.WebGLRenderer({ canvas, antialias: true, alpha: true });
  renderer.setPixelRatio(Math.min(window.devicePixelRatio, 2));
  renderer.setSize(W, H);
  renderer.setClearColor(0x000000, 0);

  const scene  = new THREE.Scene();
  const camera = new THREE.PerspectiveCamera(60, W / H, 0.1, 200);
  camera.position.set(0, 0, 28);

  /* ── Accent colour ────────────────────────────────────────────────── */
  const ACCENT = 0x4ade80;

  /* ── Cube group ───────────────────────────────────────────────────── */
  const CUBE_COUNT = 38;
  const cubes = [];
  const geo = new THREE.BoxGeometry(1, 1, 1);

  /* slightly emissive face material — very dark green tint */
  const faceMat = new THREE.MeshStandardMaterial({
    color: 0x0a1a0f,
    emissive: ACCENT,
    emissiveIntensity: 0.04,
    roughness: 0.9,
    metalness: 0.1,
    transparent: true,
    opacity: 0.55,
  });

  /* wireframe edges — green glow */
  const edgeGeo = new THREE.EdgesGeometry(geo);
  const edgeMat = new THREE.LineBasicMaterial({ color: ACCENT, transparent: true, opacity: 0.55 });

  for (let i = 0; i < CUBE_COUNT; i++) {
    const scale = 0.6 + Math.random() * 2.2;
    const mesh  = new THREE.Mesh(geo, faceMat.clone());
    mesh.scale.setScalar(scale);

    /* random position across a wide spread */
    mesh.position.set(
      (Math.random() - 0.5) * 60,
      (Math.random() - 0.5) * 30,
      (Math.random() - 0.5) * 20 - 5,
    );
    mesh.rotation.set(
      Math.random() * Math.PI,
      Math.random() * Math.PI,
      Math.random() * Math.PI,
    );

    const edges = new THREE.LineSegments(edgeGeo, edgeMat.clone());
    mesh.add(edges);
    scene.add(mesh);

    cubes.push({
      mesh,
      speed: {
        x: (Math.random() - 0.5) * 0.0025,
        y: (Math.random() - 0.5) * 0.0018,
        z: (Math.random() - 0.5) * 0.0012,
      },
      rotSpeed: {
        x: (Math.random() - 0.5) * 0.006,
        y: (Math.random() - 0.5) * 0.006,
        z: (Math.random() - 0.5) * 0.003,
      },
    });
  }

  /* ── Ambient + directional light ─────────────────────────────────── */
  scene.add(new THREE.AmbientLight(0xffffff, 0.4));
  const dirLight = new THREE.DirectionalLight(ACCENT, 0.6);
  dirLight.position.set(5, 10, 8);
  scene.add(dirLight);

  /* ── Mouse parallax ───────────────────────────────────────────────── */
  let mouseX = 0, mouseY = 0;
  let targetX = 0, targetY = 0;

  document.addEventListener('mousemove', e => {
    mouseX = (e.clientX / window.innerWidth  - 0.5) * 2;
    mouseY = (e.clientY / window.innerHeight - 0.5) * 2;
  });

  /* ── Resize handler ───────────────────────────────────────────────── */
  function onResize() {
    const w = hero.clientWidth;
    const h = hero.clientHeight || 520;
    renderer.setSize(w, h);
    camera.aspect = w / h;
    camera.updateProjectionMatrix();
  }
  window.addEventListener('resize', onResize);

  /* ── Animation loop ───────────────────────────────────────────────── */
  const BOUNDS = { x: 32, y: 18, z: 14 };

  function animate() {
    requestAnimationFrame(animate);

    /* smooth parallax */
    targetX += (mouseX - targetX) * 0.04;
    targetY += (mouseY - targetY) * 0.04;
    scene.rotation.y = targetX * 0.12;
    scene.rotation.x = -targetY * 0.08;

    cubes.forEach(c => {
      const m = c.mesh;
      m.position.x += c.speed.x;
      m.position.y += c.speed.y;
      m.position.z += c.speed.z;
      m.rotation.x  += c.rotSpeed.x;
      m.rotation.y  += c.rotSpeed.y;
      m.rotation.z  += c.rotSpeed.z;

      /* wrap around bounds */
      if (Math.abs(m.position.x) > BOUNDS.x) c.speed.x *= -1;
      if (Math.abs(m.position.y) > BOUNDS.y) c.speed.y *= -1;
      if (Math.abs(m.position.z) > BOUNDS.z) c.speed.z *= -1;
    });

    renderer.render(scene, camera);
  }

  animate();
})();
