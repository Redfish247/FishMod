package fishmod.compat;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.Window;

public final class GLFW {
    private GLFW() {}
    //? if <26.3 {
    public static final int GLFW_PRESS = org.lwjgl.glfw.GLFW.GLFW_PRESS;
    public static final InputConstants.Type KEYSYM = InputConstants.Type.KEYSYM;
    public static final int GLFW_KEY_0 = org.lwjgl.glfw.GLFW.GLFW_KEY_0;
    public static final int GLFW_KEY_1 = org.lwjgl.glfw.GLFW.GLFW_KEY_1;
    public static final int GLFW_KEY_2 = org.lwjgl.glfw.GLFW.GLFW_KEY_2;
    public static final int GLFW_KEY_3 = org.lwjgl.glfw.GLFW.GLFW_KEY_3;
    public static final int GLFW_KEY_4 = org.lwjgl.glfw.GLFW.GLFW_KEY_4;
    public static final int GLFW_KEY_6 = org.lwjgl.glfw.GLFW.GLFW_KEY_6;
    public static final int GLFW_KEY_9 = org.lwjgl.glfw.GLFW.GLFW_KEY_9;
    public static final int GLFW_KEY_A = org.lwjgl.glfw.GLFW.GLFW_KEY_A;
    public static final int GLFW_KEY_BACKSPACE = org.lwjgl.glfw.GLFW.GLFW_KEY_BACKSPACE;
    public static final int GLFW_KEY_C = org.lwjgl.glfw.GLFW.GLFW_KEY_C;
    public static final int GLFW_KEY_DELETE = org.lwjgl.glfw.GLFW.GLFW_KEY_DELETE;
    public static final int GLFW_KEY_DOWN = org.lwjgl.glfw.GLFW.GLFW_KEY_DOWN;
    public static final int GLFW_KEY_END = org.lwjgl.glfw.GLFW.GLFW_KEY_END;
    public static final int GLFW_KEY_ENTER = org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER;
    public static final int GLFW_KEY_ESCAPE = org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE;
    public static final int GLFW_KEY_F = org.lwjgl.glfw.GLFW.GLFW_KEY_F;
    public static final int GLFW_KEY_F1 = org.lwjgl.glfw.GLFW.GLFW_KEY_F1;
    public static final int GLFW_KEY_G = org.lwjgl.glfw.GLFW.GLFW_KEY_G;
    public static final int GLFW_KEY_H = org.lwjgl.glfw.GLFW.GLFW_KEY_H;
    public static final int GLFW_KEY_HOME = org.lwjgl.glfw.GLFW.GLFW_KEY_HOME;
    public static final int GLFW_KEY_KP_1 = org.lwjgl.glfw.GLFW.GLFW_KEY_KP_1;
    public static final int GLFW_KEY_KP_2 = org.lwjgl.glfw.GLFW.GLFW_KEY_KP_2;
    public static final int GLFW_KEY_KP_3 = org.lwjgl.glfw.GLFW.GLFW_KEY_KP_3;
    public static final int GLFW_KEY_KP_4 = org.lwjgl.glfw.GLFW.GLFW_KEY_KP_4;
    public static final int GLFW_KEY_KP_ENTER = org.lwjgl.glfw.GLFW.GLFW_KEY_KP_ENTER;
    public static final int GLFW_KEY_L = org.lwjgl.glfw.GLFW.GLFW_KEY_L;
    public static final int GLFW_KEY_LEFT = org.lwjgl.glfw.GLFW.GLFW_KEY_LEFT;
    public static final int GLFW_KEY_LEFT_ALT = org.lwjgl.glfw.GLFW.GLFW_KEY_LEFT_ALT;
    public static final int GLFW_KEY_LEFT_CONTROL = org.lwjgl.glfw.GLFW.GLFW_KEY_LEFT_CONTROL;
    public static final int GLFW_KEY_LEFT_SHIFT = org.lwjgl.glfw.GLFW.GLFW_KEY_LEFT_SHIFT;
    public static final int GLFW_KEY_R = org.lwjgl.glfw.GLFW.GLFW_KEY_R;
    public static final int GLFW_KEY_RIGHT = org.lwjgl.glfw.GLFW.GLFW_KEY_RIGHT;
    public static final int GLFW_KEY_RIGHT_ALT = org.lwjgl.glfw.GLFW.GLFW_KEY_RIGHT_ALT;
    public static final int GLFW_KEY_RIGHT_CONTROL = org.lwjgl.glfw.GLFW.GLFW_KEY_RIGHT_CONTROL;
    public static final int GLFW_KEY_RIGHT_SHIFT = org.lwjgl.glfw.GLFW.GLFW_KEY_RIGHT_SHIFT;
    public static final int GLFW_KEY_TAB = org.lwjgl.glfw.GLFW.GLFW_KEY_TAB;
    public static final int GLFW_KEY_UNKNOWN = org.lwjgl.glfw.GLFW.GLFW_KEY_UNKNOWN;
    public static final int GLFW_KEY_UP = org.lwjgl.glfw.GLFW.GLFW_KEY_UP;
    public static final int GLFW_KEY_V = org.lwjgl.glfw.GLFW.GLFW_KEY_V;
    public static final int GLFW_KEY_X = org.lwjgl.glfw.GLFW.GLFW_KEY_X;
    public static final int GLFW_KEY_Y = org.lwjgl.glfw.GLFW.GLFW_KEY_Y;
    public static final int GLFW_KEY_Z = org.lwjgl.glfw.GLFW.GLFW_KEY_Z;
    public static final int GLFW_MOD_SHIFT = org.lwjgl.glfw.GLFW.GLFW_MOD_SHIFT;
    public static final int GLFW_MOD_CONTROL = org.lwjgl.glfw.GLFW.GLFW_MOD_CONTROL;
    public static final int GLFW_MOD_ALT = org.lwjgl.glfw.GLFW.GLFW_MOD_ALT;
    public static final int GLFW_MOD_SUPER = org.lwjgl.glfw.GLFW.GLFW_MOD_SUPER;
    public static final int GLFW_MOUSE_BUTTON_4 = org.lwjgl.glfw.GLFW.GLFW_MOUSE_BUTTON_4;
    public static final int GLFW_MOUSE_BUTTON_RIGHT = org.lwjgl.glfw.GLFW.GLFW_MOUSE_BUTTON_RIGHT;
    //?} else {
    /*public static final int GLFW_PRESS = 1;
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
    public static final int GLFW_MOUSE_BUTTON_4 = 3;
    public static final int GLFW_MOUSE_BUTTON_RIGHT = 1;
    *///?}

    //? if <26.3 {
    public static int glfwGetKey(long window, int key) { return org.lwjgl.glfw.GLFW.glfwGetKey(window, key); }
    public static int glfwGetMouseButton(long window, int button) { return org.lwjgl.glfw.GLFW.glfwGetMouseButton(window, button); }
    public static void glfwSetCursorPos(long window, double x, double y) { org.lwjgl.glfw.GLFW.glfwSetCursorPos(window, x, y); }
    public static boolean keyDown(Window window, int key) { return InputConstants.isKeyDown(window, key); }
    public static void openUri(java.net.URI uri) { net.minecraft.util.Util.getPlatform().openUri(uri); }
    public static final int KEY_MOUSE_RIGHT = org.lwjgl.glfw.GLFW.GLFW_MOUSE_BUTTON_RIGHT;
    public static int btn(net.minecraft.client.input.MouseButtonEvent click) { return click.button(); }
    //?} else {
    /*public static int glfwGetKey(long window, int key) { return InputConstants.isKeyDown(key) ? 1 : 0; }
    public static int glfwGetMouseButton(long window, int button) {
        int mask = org.lwjgl.sdl.SDLMouse.SDL_GetMouseState((java.nio.FloatBuffer) null, (java.nio.FloatBuffer) null);
        return (mask & (1 << (button - 1))) != 0 ? 1 : 0;
    }
    public static void glfwSetCursorPos(long window, double x, double y) { org.lwjgl.sdl.SDLMouse.SDL_WarpMouseInWindow(window, (float) x, (float) y); }
    public static boolean keyDown(Window window, int key) { return InputConstants.isKeyDown(key); }
    public static void openUri(java.net.URI uri) { org.lwjgl.sdl.SDLMisc.SDL_OpenURL(uri.toString()); }
    public static final int KEY_MOUSE_RIGHT = InputConstants.MOUSE_BUTTON_RIGHT;
    public static int btn(net.minecraft.client.input.MouseButtonEvent click) {
        int b = click.button();
        return b == 1 ? 0 : b == 3 ? 1 : b == 2 ? 2 : b - 1;
    }
    *///?}
}
