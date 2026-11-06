package fishmod.compat;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.Window;

// 26.3 copy: Minecraft moved from GLFW to SDL3, so keys are SDL scancodes and mouse buttons are 1-based.
public final class GLFW {
    private GLFW() {}
    public static final int GLFW_PRESS = 1;
    public static final InputConstants.Type KEYSYM = InputConstants.Type.KEYBOARD;
    public static final int GLFW_KEY_0 = InputConstants.KEY_0;
    public static final int GLFW_KEY_1 = InputConstants.KEY_1;
    public static final int GLFW_KEY_2 = InputConstants.KEY_2;
    public static final int GLFW_KEY_3 = InputConstants.KEY_3;
    public static final int GLFW_KEY_4 = InputConstants.KEY_4;
    public static final int GLFW_KEY_6 = InputConstants.KEY_6;
    public static final int GLFW_KEY_9 = InputConstants.KEY_9;
    public static final int GLFW_KEY_A = InputConstants.KEY_A;
    public static final int GLFW_KEY_BACKSPACE = InputConstants.KEY_BACKSPACE;
    public static final int GLFW_KEY_C = InputConstants.KEY_C;
    public static final int GLFW_KEY_DELETE = InputConstants.KEY_DELETE;
    public static final int GLFW_KEY_DOWN = InputConstants.KEY_DOWN;
    public static final int GLFW_KEY_END = InputConstants.KEY_END;
    public static final int GLFW_KEY_ENTER = InputConstants.KEY_RETURN;
    public static final int GLFW_KEY_ESCAPE = InputConstants.KEY_ESCAPE;
    public static final int GLFW_KEY_F = InputConstants.KEY_F;
    public static final int GLFW_KEY_F1 = InputConstants.KEY_F1;
    public static final int GLFW_KEY_G = InputConstants.KEY_G;
    public static final int GLFW_KEY_H = InputConstants.KEY_H;
    public static final int GLFW_KEY_HOME = InputConstants.KEY_HOME;
    public static final int GLFW_KEY_KP_1 = InputConstants.KEY_NUMPAD1;
    public static final int GLFW_KEY_KP_2 = InputConstants.KEY_NUMPAD2;
    public static final int GLFW_KEY_KP_3 = InputConstants.KEY_NUMPAD3;
    public static final int GLFW_KEY_KP_4 = InputConstants.KEY_NUMPAD4;
    public static final int GLFW_KEY_KP_ENTER = InputConstants.KEY_NUMPADENTER;
    public static final int GLFW_KEY_L = InputConstants.KEY_L;
    public static final int GLFW_KEY_LEFT = InputConstants.KEY_LEFT;
    public static final int GLFW_KEY_LEFT_ALT = InputConstants.KEY_LALT;
    public static final int GLFW_KEY_LEFT_CONTROL = InputConstants.KEY_LCONTROL;
    public static final int GLFW_KEY_LEFT_SHIFT = InputConstants.KEY_LSHIFT;
    public static final int GLFW_KEY_R = InputConstants.KEY_R;
    public static final int GLFW_KEY_RIGHT = InputConstants.KEY_RIGHT;
    public static final int GLFW_KEY_RIGHT_ALT = InputConstants.KEY_RALT;
    public static final int GLFW_KEY_RIGHT_CONTROL = InputConstants.KEY_RCONTROL;
    public static final int GLFW_KEY_RIGHT_SHIFT = InputConstants.KEY_RSHIFT;
    public static final int GLFW_KEY_TAB = InputConstants.KEY_TAB;
    public static final int GLFW_KEY_UNKNOWN = 0;
    public static final int GLFW_KEY_UP = InputConstants.KEY_UP;
    public static final int GLFW_KEY_V = InputConstants.KEY_V;
    public static final int GLFW_KEY_X = InputConstants.KEY_X;
    public static final int GLFW_KEY_Y = InputConstants.KEY_Y;
    public static final int GLFW_KEY_Z = InputConstants.KEY_Z;
    public static final int GLFW_MOD_SHIFT = InputConstants.MOD_SHIFT;
    public static final int GLFW_MOD_CONTROL = InputConstants.MOD_CONTROL;
    public static final int GLFW_MOD_ALT = InputConstants.MOD_ALT;
    public static final int GLFW_MOD_SUPER = InputConstants.MOD_SUPER;
    public static final int GLFW_MOUSE_BUTTON_4 = InputConstants.MOUSE_BUTTON_4;
    public static final int GLFW_MOUSE_BUTTON_RIGHT = InputConstants.MOUSE_BUTTON_RIGHT;

    public static int glfwGetKey(long window, int key) { return InputConstants.isKeyDown(key) ? 1 : 0; }
    public static int glfwGetMouseButton(long window, int button) {
        int mask = org.lwjgl.sdl.SDLMouse.SDL_GetMouseState((java.nio.FloatBuffer) null, (java.nio.FloatBuffer) null);
        return (mask & (1 << (button - 1))) != 0 ? 1 : 0;
    }
    public static void glfwSetCursorPos(long window, double x, double y) { org.lwjgl.sdl.SDLMouse.SDL_WarpMouseInWindow(window, (float) x, (float) y); }
    public static boolean keyDown(Window window, int key) { return InputConstants.isKeyDown(key); }
    public static void openUri(java.net.URI uri) { org.lwjgl.sdl.SDLMisc.SDL_OpenURL(uri.toString()); }
}
