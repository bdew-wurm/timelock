package net.bdew.wurm.timelock;

import com.wurmonline.client.renderer.gui.HeadsUpDisplay;
import javassist.CannotCompileException;
import javassist.ClassPool;
import javassist.CtClass;
import javassist.expr.ExprEditor;
import javassist.expr.MethodCall;
import org.gotti.wurmunlimited.modloader.classhooks.HookManager;
import org.gotti.wurmunlimited.modloader.interfaces.Initable;
import org.gotti.wurmunlimited.modloader.interfaces.PreInitable;
import org.gotti.wurmunlimited.modloader.interfaces.WurmMod;

import java.util.logging.Level;
import java.util.logging.Logger;

public class TimeLockMod implements WurmMod, Initable, PreInitable {
    private static final Logger logger = Logger.getLogger("TimeLockMod");

    public static long timeLock = -1;
    public static HeadsUpDisplay hud;


    public static void logException(String msg, Throwable e) {
        if (logger != null)
            logger.log(Level.SEVERE, msg, e);
    }

    public static boolean handleInput(final String cmd, final String[] data) {
        if (cmd.equals("timelock")) {
            if (data.length == 2) {
                if (data[1].equals("off")) {
                    timeLock = -1;
                    hud.consoleOutput("Time of day is now unlocked");
                    return true;
                } else {
                    try {
                        int hours = -1, minutes = -1;
                        if (data[1].indexOf(':') > 0) {
                            String[] t = data[1].trim().split(":");
                            if (t.length == 2) {
                                hours = Integer.parseInt(t[0], 10);
                                minutes = Integer.parseInt(t[1], 10);
                            }
                        } else {
                            hours = Integer.parseInt(data[1], 10);
                            minutes = 0;
                        }
                        if (hours >= 0 && hours < 24 && minutes >= 0 && minutes < 60) {
                            timeLock = hud.getWorld().getWurmTime() / (60 * 60 * 24) * (60 * 60 * 24) + (hours * 60 * 60 + minutes * 60);
                            hud.consoleOutput(String.format("Time of day is now locked to %02d:%02d", hours, minutes));
                            return true;
                        }
                    } catch (Throwable e) {
                        logException("Error parsing time", e);
                    }
                }
            }
            hud.consoleOutput("Usage: timelock {HH[:MM]|off}");
            return true;
        }
        return false;
    }

    @Override
    public void init() {
        try {
            ClassPool classPool = HookManager.getInstance().getClassPool();

            CtClass ctWurmConsole = classPool.getCtClass("com.wurmonline.client.console.WurmConsole");
            ctWurmConsole.getMethod("handleDevInput", "(Ljava/lang/String;[Ljava/lang/String;)Z").insertBefore(
                    "if (net.bdew.wurm.timelock.TimeLockMod.handleInput($1,$2)) return true;"
            );

            CtClass ctSky = classPool.getCtClass("com.wurmonline.client.renderer.terrain.sky.Sky");
            ctSky.getMethod("setTime", "()V").instrument(new ExprEditor() {
                @Override
                public void edit(MethodCall m) throws CannotCompileException {
                    if (m.getMethodName().equals("getWurmTime")) {
                        m.replace("if (net.bdew.wurm.timelock.TimeLockMod.timeLock>=0) $_=net.bdew.wurm.timelock.TimeLockMod.timeLock; else $_=$proceed($$);");
                    }
                }
            });

            HookManager.getInstance().registerHook("com.wurmonline.client.renderer.gui.HeadsUpDisplay", "init", "(II)V", () -> (proxy, method, args) -> {
                method.invoke(proxy, args);
                hud = (HeadsUpDisplay) proxy;
                return null;
            });
        } catch (Throwable e) {
            logException("Error loading mod", e);
        }
    }

    @Override
    public void preInit() {

    }
}
