#define _DARWIN_C_SOURCE
#include <sys/file.h>
#include <sys/wait.h>
#include <sys/stat.h>
#include <fcntl.h>
#include <unistd.h>
#include <signal.h>
#include <errno.h>
#include <stdio.h>
#include <stdlib.h>
static volatile sig_atomic_t child=0;
static void forward(int sig){if(child>0)kill(child,sig);}
int main(int argc,char **argv){
 if(argc<4){fprintf(stderr,"Usage: lock <file> <command> [args...]\n");return 2;}
 int fd=open(argv[1],O_CREAT|O_RDWR|O_NOFOLLOW,0600);if(fd<0){perror("open deployment lock");return 3;}
 struct stat st;if(fstat(fd,&st)||!S_ISREG(st.st_mode)){fprintf(stderr,"Lock must be a regular file\n");return 3;}
 if(flock(fd,LOCK_EX|LOCK_NB)){fprintf(stderr,"Deployment lock is held by another session\n");close(fd);return 75;}
 char descriptor[32];snprintf(descriptor,sizeof(descriptor),"%d",fd);setenv("DAMGOX2_DEPLOY_LOCK_FD",descriptor,1);signal(SIGTERM,forward);signal(SIGINT,forward);signal(SIGHUP,forward);
 child=fork();if(child==0){execvp(argv[2],&argv[2]);perror("exec deployment");_exit(127);}if(child<0){perror("fork");close(fd);return 4;}
 int status;while(waitpid(child,&status,0)<0)if(errno!=EINTR){close(fd);return 5;}
 flock(fd,LOCK_UN);close(fd);return WIFEXITED(status)?WEXITSTATUS(status):128+WTERMSIG(status);
}
