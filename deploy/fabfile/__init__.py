# -*- coding: utf-8 -*-
from fabric.api import *
from fabric.decorators import runs_once
from datetime import datetime

env.hosts = ['your production ip']
env.user = 'your user name'
env.key_filename = 'your ssh key path'
env.project_root = '../service/crawler/'
env.branch = 'master'
env.archive = ''
env.server_dir = '~/streamCrawler'

@task
@runs_once
def deploy():
    timestamp = datetime.now().strftime("%Y%m%d_%H%M%S")
    env.archive = "%(timestamp)s.tar.gz" % {"timestamp":timestamp}
    with lcd(env.project_root):
         local("/usr/local/bin/sbt clean assembly")
         local("tar cvfz %(archive)s standalone.jar" % {"archive": env.archive})
         put("%(archive)s" % {"archive":env.archive}, "%(dir)s" % {"dir" : env.server_dir})
         local("rm %(archive)s" % {"archive": env.archive})
    run("sudo rm -Rf %(dir)s/standalone.jar" % {"dir" : env.server_dir})
    run("tar zxvf %(dir)s/%(archive)s -C %(dir)s" % {"dir" : env.server_dir, "archive": env.archive})
