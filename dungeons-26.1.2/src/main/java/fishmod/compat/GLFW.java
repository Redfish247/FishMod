package fishmod.compat;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.Window;

// Input constants/calls: GLFW values on 26.1.2/26.2 (the 26.3 copy maps to SDL).
public final class GLFW {
    private GLFW() {}
    public static final int GLFW_PRESS = fishmod.compat.GLFW.GLFW_PRESS;
    public static final InputConstants.Type KEYSYM = fishmod.compat.GLFW.KEYSYM;
    public static final int GLFW_KEY_0 = fishmod.compat.GLFW.GLFW_KEY_0;
    public static final int GLFW_KEY_1 = fishmod.compat.GLFW.GLFW_KEY_1;
    public static final int GLFW_KEY_2 = fishmod.compat.GLFW.GLFW_KEY_2;
    public static final int GLFW_KEY_3 = fishmod.compat.GLFW.GLFW_KEY_3;
    public static final int GLFW_KEY_4 = fishmod.compat.GLFW.GLFW_KEY_4;
    public static final int GLFW_KEY_6 = fishmod.compat.GLFW.GLFW_KEY_6;
    public static final int GLFW_KEY_9 = fishmod.compat.GLFW.GLFW_KEY_9;
    public static final int GLFW_KEY_A = fishmod.compat.GLFW.GLFW_KEY_A;
    public static final int GLFW_KEY_BACKSPACE = fishmod.compat.GLFW.GLFW_KEY_BACKSPACE;
    public static final int GLFW_KEY_C = fishmod.compat.GLFW.GLFW_KEY_C;
    public static final int GLFW_KEY_DELETE = fishmod.compat.GLFW.GLFW_KEY_DELETE;
    public static final int GLFW_KEY_DOWN = fishmod.compat.GLFW.GLFW_KEY_DOWN;
    public static final int GLFW_KEY_END = fishmod.compat.GLFW.GLFW_KEY_END;
    public static final int GLFW_KEY_ENTER = fishmod.compat.GLFW.GLFW_KEY_ENTER;
    public static final int GLFW_KEY_ESCAPE = fishmod.compat.GLFW.GLFW_KEY_ESCAPE;
    public static final int GLFW_KEY_F = fishmod.compat.GLFW.GLFW_KEY_F;
    public static final int GLFW_KEY_F1 = fishmod.compat.GLFW.GLFW_KEY_F1;
    public static final int GLFW_KEY_G = fishmod.compat.GLFW.GLFW_KEY_G;
    public static final int GLFW_KEY_H = fishmod.compat.GLFW.GLFW_KEY_H;
    public static final int GLFW_KEY_HOME = fishmod.compat.GLFW.GLFW_KEY_HOME;
    public static final int GLFW_KEY_KP_1 = fishmod.compat.GLFW.GLFW_KEY_KP_1;
    public static final int GLFW_KEY_KP_2 = fishmod.compat.GLFW.GLFW_KEY_KP_2;
    public static final int GLFW_KEY_KP_3 = fishmod.compat.GLFW.GLFW_KEY_KP_3;
    public static final int GLFW_KEY_KP_4 = fishmod.compat.GLFW.GLFW_KEY_KP_4;
    public static final int GLFW_KEY_KP_ENTER = fishmod.compat.GLFW.GLFW_KEY_KP_ENTER;
    public static final int GLFW_KEY_L = fishmod.compat.GLFW.GLFW_KEY_L;
    public static final int GLFW_KEY_LEFT = fishmod.compat.GLFW.GLFW_KEY_LEFT;
    public static final int GLFW_KEY_LEFT_ALT = fishmod.compat.GLFW.GLFW_KEY_LEFT_ALT;
    public static final int GLFW_KEY_LEFT_CONTROL = fishmod.compat.GLFW.GLFW_KEY_LEFT_CONTROL;
    public static final int GLFW_KEY_LEFT_SHIFT = fishmod.compat.GLFW.GLFW_KEY_LEFT_SHIFT;
    public static final int GLFW_KEY_R = fishmod.compat.GLFW.GLFW_KEY_R;
    public static final int GLFW_KEY_RIGHT = fishmod.compat.GLFW.GLFW_KEY_RIGHT;
    public static final int GLFW_KEY_RIGHT_ALT = fishmod.compat.GLFW.GLFW_KEY_RIGHT_ALT;
    public static final int GLFW_KEY_RIGHT_CONTROL = fishmod.compat.GLFW.GLFW_KEY_RIGHT_CONTROL;
    public static final int GLFW_KEY_RIGHT_SHIFT = fishmod.compat.GLFW.GLFW_KEY_RIGHT_SHIFT;
    public static final int GLFW_KEY_TAB = fishmod.compat.GLFW.GLFW_KEY_TAB;
    public static final int GLFW_KEY_UNKNOWN = fishmod.compat.GLFW.GLFW_KEY_UNKNOWN;
    public static final int GLFW_KEY_UP = fishmod.compat.GLFW.GLFW_KEY_UP;
    public static final int GLFW_KEY_V = fishmod.compat.GLFW.GLFW_KEY_V;
    public static final int GLFW_KEY_X = fishmod.compat.GLFW.GLFW_KEY_X;
    public static final int GLFW_KEY_Y = fishmod.compat.GLFW.GLFW_KEY_Y;
    public static final int GLFW_KEY_Z = fishmod.compat.GLFW.GLFW_KEY_Z;
    public static final int GLFW_MOD_SHIFT = fishmod.compat.GLFW.GLFW_MOD_SHIFT;
    public static final int GLFW_MOD_CONTROL = fishmod.compat.GLFW.GLFW_MOD_CONTROL;
    public static final int GLFW_MOD_ALT = fishmod.compat.GLFW.GLFW_MOD_ALT;
    public static final int GLFW_MOD_SUPER = fishmod.compat.GLFW.GLFW_MOD_SUPER;
    public static final int GLFW_MOUSE_BUTTON_4 = fishmod.compat.GLFW.GLFW_MOUSE_BUTTON_4;
    public static final int GLFW_MOUSE_BUTTON_RIGHT = fishmod.compat.GLFW.GLFW_MOUSE_BUTTON_RIGHT;

    public static int glfwGetKey(long window, int key) { return fishmod.compat.GLFW.glfwGetKey(window, key); }
    public static int glfwGetMouseButton(long window, int button) { return fishmod.compat.GLFW.glfwGetMouseButton(window, button); }
    public static void glfwSetCursorPos(long window, double x, double y) { fishmod.compat.GLFW.glfwSetCursorPos(window, x, y); }
    public static boolean keyDown(Window window, int key) { return fishmod.compat.GLFW.keyDown(window, key); }
    public static void openUri(java.net.URI uri) { fishmod.compat.GLFW.openUri(uri); }
}
