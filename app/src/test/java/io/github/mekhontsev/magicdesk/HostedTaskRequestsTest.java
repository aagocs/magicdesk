package io.github.mekhontsev.magicdesk;

import org.junit.Test;

public class HostedTaskRequestsTest {
    @Test public void protocolRequestsUseObservedTaskStateAndCannotStealBackgroundFocus() throws Exception {
        RuntimeSourceFixture.verify("io.github.mekhontsev.magicdesk", "static "
                + RuntimeSourceFixture.nestedClass("HostedTaskRequests", "HostedTaskRequests")
                        .replace("BooleanSupplier", "java.util.function.BooleanSupplier")
                        .replace("Consumer<", "java.util.function.Consumer<")
                        .replace("android.util.Log", "Log")
                        .replace("HostedWindowInteraction", "io.github.mekhontsev.magicdesk.hosted.HostedWindowInteraction")
                + """
            static class Log {static void w(String tag,String message,Throwable error){}}
            static class Activity {
                boolean focus, reject; int backs;
                boolean hasWindowFocus(){return focus;} int getTaskId(){return 42;}
                Activity getDisplay(){return this;} int getDisplayId(){return 7;}
                void runOnUiThread(Runnable work){work.run();}
                boolean moveTaskToBack(boolean root){if(reject)throw new SecurityException();backs++;return true;}
                <T> T getSystemService(Class<T> type){return type.cast(new ActivityManager());}
            }
            static class ActivityManager {
                List<AppTask> getAppTasks(){return List.of(new AppTask());}
                static class AppTask {
                    int taskId=42; AppTask getTaskInfo(){return this;} void moveToFront(){}
                }
            }
            static class TaskRepository {
                record Result(boolean success){}
                interface ActionCallback {void onComplete(Result result);}
                static class Entry {int taskId=42;}
                static class Snapshot {boolean available=true; List<Entry> tasks=List.of(new Entry());}
            }
            static class DesktopRuntimeBridge {
                static boolean workspace=true;
                static int refreshes;
                static boolean hasWorkspace(int display){check(display==7,"display");return workspace;}
                static void refreshTaskPresentations(){refreshes++;}
            }
            static class MagicDeskRuntime {
                static int activations,minimizations;
                static boolean minimized;
                static boolean known=true, owned=true;
                static TaskRepository.Snapshot observedTaskSnapshot(int display){return known?new TaskRepository.Snapshot():null;}
                static TaskRepository.Snapshot selectDesktopTaskSnapshot(int display,TaskRepository.Snapshot observed){
                    var selected=new TaskRepository.Snapshot(); if(!owned)selected.tasks=List.of(); return selected;
                }
                static TaskRepository.ActionCallback pending;
                static boolean isTaskConcealed(int display,int task){return minimized;}
                static void concealTask(int display,int task,TaskRepository.ActionCallback done){minimizations++;pending=done;}
                static void focusDesktopTask(int display,int task,TaskRepository.ActionCallback done){activations++;pending=done;}
            }
            public static void verify() {
                var activity=new Activity(); boolean[] foreground={false};
                var states=new ArrayList<io.github.mekhontsev.magicdesk.hosted.HostedWindowInteraction.State>();
                var controller=new HostedTaskRequests(activity,()->foreground[0],states::add);
                controller.update(new io.github.mekhontsev.magicdesk.hosted.HostedWindowInteraction(1,
                    io.github.mekhontsev.magicdesk.hosted.HostedWindowInteraction.Action.ACTIVATE,false));
                check(MagicDeskRuntime.activations==0 && controller.attention(),"background request becomes attention");
                foreground[0]=true;
                controller.update(new io.github.mekhontsev.magicdesk.hosted.HostedWindowInteraction(2,
                    io.github.mekhontsev.magicdesk.hosted.HostedWindowInteraction.Action.ACTIVATE,false));
                check(MagicDeskRuntime.activations==1 && states.size()==1,"accepted request is not confirmation");
                activity.focus=true; controller.observe(); check(states.size()==1,"pending gateway still owns command");
                MagicDeskRuntime.pending.onComplete(new TaskRepository.Result(true));
                check(states.get(states.size()-1).active() && !controller.attention(),"observed activation clears attention");
                controller.update(new io.github.mekhontsev.magicdesk.hosted.HostedWindowInteraction(3,
                    io.github.mekhontsev.magicdesk.hosted.HostedWindowInteraction.Action.MINIMIZE,false));
                check(MagicDeskRuntime.minimizations==1,"managed minimize uses gateway");
                MagicDeskRuntime.minimized=true; MagicDeskRuntime.pending.onComplete(new TaskRepository.Result(true));
                check(states.get(states.size()-1).minimized() && !states.get(states.size()-1).active(),"concealed cannot be active");
                controller.update(new io.github.mekhontsev.magicdesk.hosted.HostedWindowInteraction(3,
                    io.github.mekhontsev.magicdesk.hosted.HostedWindowInteraction.Action.MINIMIZE,false));
                check(MagicDeskRuntime.minimizations==1,"snapshot replay is not another command");
                DesktopRuntimeBridge.workspace=false; activity.focus=false;
                controller.update(new io.github.mekhontsev.magicdesk.hosted.HostedWindowInteraction(4,
                    io.github.mekhontsev.magicdesk.hosted.HostedWindowInteraction.Action.MINIMIZE,false));
                check(activity.backs==1 && states.get(states.size()-1).serial()==3,"ordinary Activity path waits for onStop");
                controller.visible(false); check(states.get(states.size()-1).minimized(),"onStop confirms independent minimize");
                controller.visible(true); check(!states.get(states.size()-1).minimized(),"onStart restores state");
                activity.reject=true;
                controller.update(new io.github.mekhontsev.magicdesk.hosted.HostedWindowInteraction(5,
                    io.github.mekhontsev.magicdesk.hosted.HostedWindowInteraction.Action.MINIMIZE,false));
                check(states.get(states.size()-1).serial()==5 && !states.get(states.size()-1).minimized(),
                    "rejected Android operation cannot strand confirmation or fabricate minimization");
                activity.reject=false; DesktopRuntimeBridge.workspace=true; MagicDeskRuntime.owned=false;
                MagicDeskRuntime.known=false;
                controller.update(new io.github.mekhontsev.magicdesk.hosted.HostedWindowInteraction(6,
                    io.github.mekhontsev.magicdesk.hosted.HostedWindowInteraction.Action.MINIMIZE,false));
                check(activity.backs==1 && states.get(states.size()-1).serial()==5,"unknown ownership defers the request");
                MagicDeskRuntime.known=true; controller.observe();
                check(activity.backs==2 && MagicDeskRuntime.minimizations==1,"independent task on a Desktop display stays independent");
                controller.visible(false);
                check(states.get(states.size()-1).serial()==6 && states.get(states.size()-1).minimized(),"deferred request completes from observation");
                int count=states.size(); controller.close();controller.visible(false);controller.observe();
                check(states.size()==count,"closed hosts cannot acknowledge another owner");
            }
            """, java.nio.file.Path.of("../hosted-runtime/src/main/java/io/github/mekhontsev/magicdesk/hosted/HostedWindowInteraction.java").toAbsolutePath().toString());
    }
}
