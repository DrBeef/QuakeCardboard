#include <stdio.h>
#include <stdlib.h>
#include <math.h>
#include <time.h>
#include <unistd.h>
#include <android/log.h>
#include <android/native_window_jni.h>	// for native window JNI
#include <android/input.h>

#include <qtypes.h>
#include <quakedef.h>
#include <menu.h>

//All the functionality we link to in the DarkPlaces Quake implementation
extern void QC_BeginFrame();
extern void QC_DrawFrame(int eye, int x, int y);
extern void QC_EndFrame();
extern void QC_GetAudio();
extern void QC_KeyEvent(int state,int key,int character);
extern void QC_MoveEvent(float yaw, float pitch, float roll);
extern void QC_SetCallbacks(void *init_audio, void *write_audio);
extern void QC_SetResolution(int width, int height);
extern void QC_Analog(int enable,float x,float y);
extern void QC_MotionEvent(float delta, float dx, float dy);
extern int main (int argc, char **argv);


static JavaVM *jVM;
static jobject audioBuffer=0;
static jobject qCallbackObj=0;

jmethodID android_initAudio;
jmethodID android_writeAudio;
jmethodID android_pauseAudio;
jmethodID android_resumeAudio;
jmethodID android_terminateAudio;

void jni_initAudio(void *buffer, int size)
{
    JNIEnv *env;
    jobject tmp;
    (*jVM)->GetEnv(jVM, (void**) &env, JNI_VERSION_1_4);
    tmp = (*env)->NewDirectByteBuffer(env, buffer, size);
    audioBuffer = (jobject)(*env)->NewGlobalRef(env, tmp);
    return (*env)->CallVoidMethod(env, qCallbackObj, android_initAudio, size);
}

void jni_writeAudio(int offset, int length)
{
	if (audioBuffer==0) return;
    JNIEnv *env;
    if (((*jVM)->GetEnv(jVM, (void**) &env, JNI_VERSION_1_4))<0)
    {
    	(*jVM)->AttachCurrentThread(jVM,&env, NULL);
    }
    (*env)->CallVoidMethod(env, qCallbackObj, android_writeAudio, audioBuffer, offset, length);
}

void jni_pauseAudio()
{
	if (audioBuffer==0) return;
    JNIEnv *env;
    if (((*jVM)->GetEnv(jVM, (void**) &env, JNI_VERSION_1_4))<0)
    {
    	(*jVM)->AttachCurrentThread(jVM,&env, NULL);
    }
    (*env)->CallVoidMethod(env, qCallbackObj, android_pauseAudio);
}

void jni_resumeAudio()
{
	if (audioBuffer==0) return;
    JNIEnv *env;
    if (((*jVM)->GetEnv(jVM, (void**) &env, JNI_VERSION_1_4))<0)
    {
    	(*jVM)->AttachCurrentThread(jVM,&env, NULL);
    }
    (*env)->CallVoidMethod(env, qCallbackObj, android_resumeAudio);
}

void jni_terminateAudio()
{
	if (audioBuffer==0) return;
    JNIEnv *env;
    if (((*jVM)->GetEnv(jVM, (void**) &env, JNI_VERSION_1_4))<0)
    {
    	(*jVM)->AttachCurrentThread(jVM,&env, NULL);
    }
    (*env)->CallVoidMethod(env, qCallbackObj, android_terminateAudio);
}

//Timing stuff for joypad control
long oldtime=0;
long delta=0;
float last_joystick_x=0;
float last_joystick_y=0;

int curtime;
int Sys_Milliseconds (void)
{
	struct timeval tp;
	struct timezone tzp;
	static int		secbase;

	gettimeofday(&tp, &tzp);

	if (!secbase)
	{
		secbase = tp.tv_sec;
		return tp.tv_usec/1000;
	}

	curtime = (tp.tv_sec - secbase)*1000 + tp.tv_usec/1000;

	return curtime;
}

static const float meters_to_units = 40.0f;

//Should get this from OVR
float GVR_GetSeparation()
{
	static float separation = 0.0;
	if (separation == 0.0)
	{
		//Generic eye separation of 0.065 metres
		separation = meters_to_units * 0.065;
	}
	return separation;
}


int returnvalue = -1;
void QC_exit(int exitCode)
{
	returnvalue = exitCode;
}

vec3_t hmdorientation;

int JNI_OnLoad(JavaVM* vm, void* reserved)
{
    JNIEnv *env;
    jVM = vm;
    if((*vm)->GetEnv(vm, (void**) &env, JNI_VERSION_1_4) != JNI_OK)
    {
		return -1;
    }

    return JNI_VERSION_1_4;
}

static void UnEscapeQuotes( char *arg )
{
	char *last = NULL;
	while( *arg ) {
		if( *arg == '"' && *last == '\\' ) {
			char *c_curr = arg;
			char *c_last = last;
			while( *c_curr ) {
				*c_last = *c_curr;
				c_last = c_curr;
				c_curr++;
			}
			*c_last = '\0';
		}
		last = arg;
		arg++;
	}
}

static int ParseCommandLine(char *cmdline, char **argv)
{
	char *bufp;
	char *lastp = NULL;
	int argc, last_argc;
	argc = last_argc = 0;
	for ( bufp = cmdline; *bufp; ) {
		while ( isspace(*bufp) ) {
			++bufp;
		}
		if ( *bufp == '"' ) {
			++bufp;
			if ( *bufp ) {
				if ( argv ) {
					argv[argc] = bufp;
				}
				++argc;
			}
			while ( *bufp && ( *bufp != '"' || *lastp == '\\' ) ) {
				lastp = bufp;
				++bufp;
			}
		} else {
			if ( *bufp ) {
				if ( argv ) {
					argv[argc] = bufp;
				}
				++argc;
			}
			while ( *bufp && ! isspace(*bufp) ) {
				++bufp;
			}
		}
		if ( *bufp ) {
			if ( argv ) {
				*bufp = '\0';
			}
			++bufp;
		}
		if( argv && last_argc != argc ) {
			UnEscapeQuotes( argv[last_argc] );
		}
		last_argc = argc;
	}
	if ( argv ) {
		argv[argc] = NULL;
	}
	return(argc);
}

JNIEXPORT void JNICALL Java_com_drbeef_quakecardboard_QuakeJNILib_setResolution( JNIEnv * env, jobject obj, int width, int height )
{
	QC_SetResolution(width, height);
}

JNIEXPORT void JNICALL Java_com_drbeef_quakecardboard_QuakeJNILib_initialise( JNIEnv * env, jobject obj, jstring commandLineParams )
{
	static qboolean quake_initialised = false;
	if (!quake_initialised)
	{
		chdir("/sdcard/QGVR");
		jboolean iscopy;
		const char *arg = (*env)->GetStringUTFChars(env, commandLineParams, &iscopy);

		char *cmdLine = NULL;
		if (arg && strlen(arg))
		{
			cmdLine = strdup(arg);
		}

		(*env)->ReleaseStringUTFChars(env, commandLineParams, arg);

		if (cmdLine)
		{
			char **argv;
			int argc=0;
			argv = malloc(sizeof(char*) * 255);
			argc = ParseCommandLine(strdup(cmdLine), argv);
			main(argc, argv);
		}
		else
		{
			int argc =1; char *argv[] = { "quake" };
			main(argc, argv);
		}

		//Start game with game menu active
		MR_ToggleMenu(1);
		quake_initialised = true;
	}
}

#define YAW 1
#define PITCH 0
#define ROLL 2

JNIEXPORT void JNICALL Java_com_drbeef_quakecardboard_QuakeJNILib_onNewFrame( JNIEnv * env, jobject obj, float pitch, float yaw, float roll )
{
	long t=Sys_Milliseconds();
	delta=t-oldtime;
	oldtime=t;
	if (delta>1000)
	delta=1000;

	QC_MotionEvent(delta, last_joystick_x, last_joystick_y);

	//Save orientation
	hmdorientation[YAW] = yaw;
	hmdorientation[PITCH] = pitch;
	hmdorientation[ROLL] = roll;
	
	//Set move information
	QC_MoveEvent(hmdorientation[YAW], hmdorientation[PITCH], hmdorientation[ROLL]);

	//Set everything up
	QC_BeginFrame();
}

JNIEXPORT void JNICALL Java_com_drbeef_quakecardboard_QuakeJNILib_onDrawEye( JNIEnv * env, jobject obj, int eye, int x, int y )
{
	QC_DrawFrame(eye, x, y);

	//const GLenum depthAttachment[1] = { GL_DEPTH_ATTACHMENT };
	//glInvalidateFramebuffer( GL_FRAMEBUFFER, 1, depthAttachment );
	//glFlush();
}

JNIEXPORT void JNICALL Java_com_drbeef_quakecardboard_QuakeJNILib_onFinishFrame( JNIEnv * env, jobject obj )
{
	QC_EndFrame();
}

JNIEXPORT void JNICALL Java_com_drbeef_quakecardboard_QuakeJNILib_onKeyEvent( JNIEnv * env, jobject obj, int keyCode, int action, int character )
{
	//Dispatch to quake
	QC_KeyEvent(action == AKEY_EVENT_ACTION_DOWN ? 1 : 0, keyCode, character);
}

#define SOURCE_GAMEPAD 	0x00000401
#define SOURCE_JOYSTICK 0x01000010
JNIEXPORT void JNICALL Java_com_drbeef_quakecardboard_QuakeJNILib_onTouchEvent( JNIEnv * env, jobject obj, int source, int action, float x, float y )
{
	if (source == SOURCE_JOYSTICK || source == SOURCE_GAMEPAD)
		QC_Analog(true, x, y);
}

JNIEXPORT void JNICALL Java_com_drbeef_quakecardboard_QuakeJNILib_onMotionEvent( JNIEnv * env, jobject obj, int source, int action, float x, float y )
{
	if (source == SOURCE_JOYSTICK || source == SOURCE_GAMEPAD)
	{
		last_joystick_x=x;
	}
}

JNIEXPORT void JNICALL Java_com_drbeef_quakecardboard_QuakeJNILib_setCallbackObject(JNIEnv *env, jclass c, jobject obj)
{
    qCallbackObj = obj;
    jclass qCallbackClass;

    (*jVM)->GetEnv(jVM, (void**) &env, JNI_VERSION_1_4);
    qCallbackObj = (jobject)(*env)->NewGlobalRef(env, obj);
    qCallbackClass = (*env)->GetObjectClass(env, qCallbackObj);

    android_initAudio = (*env)->GetMethodID(env,qCallbackClass,"initAudio","(I)V");
    android_writeAudio = (*env)->GetMethodID(env,qCallbackClass,"writeAudio","(Ljava/nio/ByteBuffer;II)V");
    android_pauseAudio = (*env)->GetMethodID(env,qCallbackClass,"pauseAudio","()V");
    android_resumeAudio = (*env)->GetMethodID(env,qCallbackClass,"resumeAudio","()V");
    android_terminateAudio = (*env)->GetMethodID(env,qCallbackClass,"terminateAudio","()V");
}

JNIEXPORT void JNICALL Java_com_drbeef_quakecardboard_QuakeJNILib_requestAudioData(JNIEnv *env, jclass c, jlong handle)
{
	QC_GetAudio();
}