#define _GNU_SOURCE
#include <X11/Xlib.h>
#include <X11/Xutil.h>
#include <X11/Xatom.h>
#include <X11/extensions/XInput2.h>
#include <assert.h>
#include <fcntl.h>
#include <poll.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/stat.h>
#include <unistd.h>

/* Interactive protocol fixture. Commands arrive through the named pipe, never timers. */
static Display *display;
static Window window;
static Time last_input;
static Atom atom(const char *name) { return XInternAtom(display,name,False); }
static void request(const char *name,long a,long b,long c) {
    XEvent event={0}; event.xclient.type=ClientMessage; event.xclient.window=window;
    event.xclient.message_type=atom(name); event.xclient.format=32;
    event.xclient.data.l[0]=a;event.xclient.data.l[1]=b;event.xclient.data.l[2]=c;
    XSendEvent(display,DefaultRootWindow(display),False,SubstructureRedirectMask|SubstructureNotifyMask,&event);
}
static void command(const char *text) {
    if(!strcmp(text,"minimize")) request("WM_CHANGE_STATE",IconicState,0,0);
    else if(!strcmp(text,"activate")) request("_NET_ACTIVE_WINDOW",1,last_input,0);
    else if(!strcmp(text,"stale")) request("_NET_ACTIVE_WINDOW",1,0,0);
    else if(!strcmp(text,"attention")) request("_NET_WM_STATE",1,atom("_NET_WM_STATE_DEMANDS_ATTENTION"),0);
    else if(!strcmp(text,"urgent")) {
        XWMHints hints={.flags=InputHint|XUrgencyHint,.input=True}; XSetWMHints(display,window,&hints);
    } else if(!strcmp(text,"grid")) {
        XSizeHints hints={.flags=PMinSize|PBaseSize|PResizeInc,.min_width=110,.min_height=100,
            .base_width=10,.base_height=20,.width_inc=8,.height_inc=16};
        XSetWMNormalHints(display,window,&hints);
    } else if(!strcmp(text,"aspect")) {
        XSizeHints hints={.flags=PBaseSize|PResizeInc|PAspect,.base_width=10,.base_height=20,.width_inc=8,.height_inc=8,
            .min_aspect={1,1},.max_aspect={1,1}};
        XSetWMNormalHints(display,window,&hints);
    } else if(!strcmp(text,"guest-wm")) XSetSelectionOwner(display,atom("WM_S0"),window,CurrentTime);
    else if(!strcmp(text,"quit")) { XDestroyWindow(display,window); XCloseDisplay(display); exit(0); }
    printf("command %s\n",text); XFlush(display);
}

int main(int argc,char **argv) {
    assert(argc==2 || argc==3);
    setvbuf(stdout,NULL,_IOLBF,0);
    display=XOpenDisplay(NULL); assert(display);
    int opcode,first,error,major=2,minor=2;
    assert(XQueryExtension(display,"XInputExtension",&opcode,&first,&error));
    assert(XIQueryVersion(display,&major,&minor)==Success);
    window=XCreateSimpleWindow(display,DefaultRootWindow(display),0,0,640,480,0,0,0x246454);
    XStoreName(display,window,"MagicDesk input and window protocol checks");
    XClassHint hint={"magicdesk-checks","MagicDeskChecks"}; XSetClassHint(display,window,&hint);
    XSelectInput(display,window,StructureNotifyMask|PropertyChangeMask|ButtonPressMask|ButtonReleaseMask|KeyPressMask);
    if(argc==2) {
        unsigned char bits[XIMaskLen(XI_LASTEVENT)]={0};
        XISetMask(bits,XI_Motion);XISetMask(bits,XI_ButtonPress);XISetMask(bits,XI_ButtonRelease);
        XISetMask(bits,XI_TouchBegin);XISetMask(bits,XI_TouchUpdate);XISetMask(bits,XI_TouchEnd);
        XIEventMask mask={XIAllMasterDevices,sizeof(bits),bits}; XISelectEvents(display,window,&mask,1);
    }
    XMapWindow(display,window); XFlush(display);
    printf("window %lu\n",window);
    assert(mkfifo(argv[1],0600)==0); int commands=open(argv[1],O_RDWR|O_NONBLOCK|O_CLOEXEC); assert(commands>=0);
    for(;;) {
        while(XPending(display)) {
            XEvent event; XNextEvent(display,&event);
            if(event.type==GenericEvent && event.xcookie.extension==opcode && XGetEventData(display,&event.xcookie)) {
                XIDeviceEvent *input=event.xcookie.data;
                if(input->evtype==XI_TouchBegin || input->evtype==XI_ButtonPress) last_input=input->time;
                printf("xi type=%d source=%d detail=%d flags=%d xy=%.2f,%.2f axes=",input->evtype,input->sourceid,input->detail,input->flags,input->event_x,input->event_y);
                double *value=input->valuators.values;
                for(int i=0;i<input->valuators.mask_len*8;i++) if(XIMaskIsSet(input->valuators.mask,i)) printf("%d:%.4f,",i,*value++);
                puts("");XFreeEventData(display,&event.xcookie);
            } else if(event.type==ButtonPress || event.type==ButtonRelease) {
                if(event.type==ButtonPress) last_input=event.xbutton.time;
                printf("core type=%d button=%u\n",event.type,event.xbutton.button);
            } else if(event.type==ConfigureNotify) printf("size %d %d\n",event.xconfigure.width,event.xconfigure.height);
            else if(event.type==PropertyNotify && (event.xproperty.atom==atom("WM_STATE") || event.xproperty.atom==atom("_NET_WM_STATE"))) {
                Atom type;int format;unsigned long count,after;unsigned char *bytes=NULL;
                XGetWindowProperty(display,window,event.xproperty.atom,0,64,False,AnyPropertyType,&type,&format,&count,&after,&bytes);
                char *name=XGetAtomName(display,event.xproperty.atom); printf("state %s",name);XFree(name);
                for(unsigned long i=0;bytes && format==32 && i<count;i++) {
                    unsigned long value=((unsigned long*)bytes)[i];
                    if(type==XA_ATOM){name=XGetAtomName(display,value);printf(" %s",name);XFree(name);}
                    else printf(" %lu",value);
                }
                puts("");if(bytes)XFree(bytes);
            }
        }
        struct pollfd fds[]={{ConnectionNumber(display),POLLIN,0},{commands,POLLIN,0}};
        // EVENT_WAIT: X events or explicit test commands; the fixture owner bounds process lifetime.
        assert(poll(fds,2,-1)>=0);
        if(fds[1].revents&POLLIN) {
            char input[256];ssize_t size=read(commands,input,sizeof(input)-1); assert(size>0);input[size]=0;
            char *save=NULL;for(char *line=strtok_r(input,"\n",&save);line;line=strtok_r(NULL,"\n",&save)) command(line);
        }
    }
}
