package io.github.rpgclasses.client.tree;

/**
 * Cámara del árbol: punto del árbol que queda en el centro de la vista y zoom. El zoom y los desplazamientos
 * largos (centrar, minimapa) se animan suavemente. Lógica pura.
 */
public final class TreeCamera {
    public static final float MIN_ZOOM = 0.35f, MAX_ZOOM = 1.75f;

    public float x, y, zoom = 1;
    private float targetZoom = 1;
    /** Punto del árbol que debe quedarse bajo el ratón mientras el zoom se anima. */
    private float anchorTreeX, anchorTreeY, anchorScreenX, anchorScreenY;
    private boolean anchored;
    private float goalX, goalY;
    private boolean gliding;

    /** Rectángulo de la vista en pantalla. */
    public int viewX0, viewY0, viewX1, viewY1;

    public void setView(int x0, int y0, int x1, int y1) {
        viewX0 = x0;
        viewY0 = y0;
        viewX1 = x1;
        viewY1 = y1;
    }

    public float centerScreenX() {
        return (viewX0 + viewX1) / 2f;
    }

    public float centerScreenY() {
        return (viewY0 + viewY1) / 2f;
    }

    public float sx(float treeX) {
        return centerScreenX() + (treeX - x) * zoom;
    }

    public float sy(float treeY) {
        return centerScreenY() + (treeY - y) * zoom;
    }

    public float treeX(double screenX) {
        return x + (float) (screenX - centerScreenX()) / zoom;
    }

    public float treeY(double screenY) {
        return y + (float) (screenY - centerScreenY()) / zoom;
    }

    public boolean contains(double sx, double sy) {
        return sx >= viewX0 && sx < viewX1 && sy >= viewY0 && sy < viewY1;
    }

    /** Coloca la cámara sin animación. */
    public void set(float cx, float cy, float z) {
        x = cx;
        y = cy;
        zoom = targetZoom = clampZoom(z);
        anchored = false;
        gliding = false;
    }

    /** Arrastre en píxeles de pantalla. */
    public void pan(double dx, double dy, TreeLayout layout) {
        x -= (float) dx / zoom;
        y -= (float) dy / zoom;
        gliding = false;
        if (anchored) {
            anchorTreeX -= (float) dx / zoom;
            anchorTreeY -= (float) dy / zoom;
        }
        clamp(layout);
    }

    /** Zoom hacia el punto de pantalla indicado (la rueda). */
    public void zoomAt(double screenX, double screenY, float factor) {
        anchorScreenX = (float) screenX;
        anchorScreenY = (float) screenY;
        anchorTreeX = treeX(screenX);
        anchorTreeY = treeY(screenY);
        anchored = true;
        gliding = false;
        targetZoom = clampZoom(targetZoom * factor);
    }

    /** Desplazamiento suave hasta un punto del árbol (y, opcionalmente, un zoom). */
    public void glideTo(float tx, float ty, float z) {
        goalX = tx;
        goalY = ty;
        gliding = true;
        anchored = false;
        if (z > 0) targetZoom = clampZoom(z);
    }

    /** Avanza las animaciones; {@code dt} en segundos. */
    public void update(float dt, TreeLayout layout) {
        float k = 1 - (float) Math.exp(-dt * 14);
        if (Math.abs(targetZoom - zoom) > 1e-4f) {
            zoom += (targetZoom - zoom) * k;
            if (Math.abs(targetZoom - zoom) < 1e-4f) zoom = targetZoom;
            if (anchored) {
                x = anchorTreeX - (anchorScreenX - centerScreenX()) / zoom;
                y = anchorTreeY - (anchorScreenY - centerScreenY()) / zoom;
            }
        } else {
            anchored = false;
        }
        if (gliding) {
            float g = 1 - (float) Math.exp(-dt * 9);
            x += (goalX - x) * g;
            y += (goalY - y) * g;
            if (Math.abs(goalX - x) < 0.3f && Math.abs(goalY - y) < 0.3f) {
                x = goalX;
                y = goalY;
                gliding = false;
            }
        }
        clamp(layout);
    }

    /** El centro de la vista no puede salir del rectángulo del árbol. */
    public void clamp(TreeLayout layout) {
        if (layout == null) return;
        x = Math.max(layout.minX, Math.min(layout.maxX, x));
        y = Math.max(layout.minY, Math.min(layout.maxY, y));
    }

    /** Zoom con el que cabe en la vista la isla de clase base con todos sus brazos. */
    public float fitZoom(TreeLayout layout) {
        float w = Math.max(1, viewX1 - viewX0 - 24), h = Math.max(1, viewY1 - viewY0 - 24);
        return clampZoom(Math.min(w / (layout.baseMaxX - layout.baseMinX), h / (layout.baseMaxY - layout.baseMinY)));
    }

    /** Encuadre inicial: la isla de clase base entera. */
    public void home(TreeLayout layout, boolean animate) {
        float cx = (layout.baseMinX + layout.baseMaxX) / 2, cy = (layout.baseMinY + layout.baseMaxY) / 2;
        if (animate) glideTo(cx, cy, fitZoom(layout));
        else set(cx, cy, fitZoom(layout));
    }

    public static float clampZoom(float z) {
        return Math.max(MIN_ZOOM, Math.min(MAX_ZOOM, z));
    }
}
