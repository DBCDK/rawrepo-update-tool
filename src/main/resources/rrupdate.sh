#!/usr/bin/env bash
rrupdate_home="$HOME/.rrupdate"
rrupdate_archive="${rrupdate_home}/archive"
rrupdate_bin="${rrupdate_home}/bin"
rrupdate_url=http://mavenrepo.dbc.dk/content/repositories/releases/dk/dbc/rawrepo-update-tool

function get_current_version {
    local current_version
    if [ -f ${rrupdate_home}/version ]; then
        current_version=`cat ${rrupdate_home}/version`
    else
        current_version=0
    fi
    echo ${current_version}
}

function get_latest_version {
    local latest_version=`
        curl -s "${rrupdate_url}/maven-metadata.xml" | \
        grep "<release>.*</release>" | \
        sed -e "s#\(.*\)\(<release>\)\(.*\)\(</release>\)\(.*\)#\3#g"`
    echo ${latest_version}
}

function install {
    if [ -z $(which curl) ]; then
        echo "curl not found."
        echo ""
        echo "======================================================================================================"
        echo " Please install curl on your system using your favourite package manager."
        echo ""
        echo " Restart after installing curl."
        echo "======================================================================================================"
        echo ""
        exit 1
    fi

    if [ -z $(which unzip) ]; then
        echo "unzip not found."
        echo ""
        echo "======================================================================================================"
        echo " Please install unzip on your system using your favourite package manager."
        echo ""
        echo " Restart after installing unzip."
        echo "======================================================================================================"
        echo ""
        exit 1
    fi

    if [ -z $(which java) ]; then
        echo "java not found."
        echo ""
        echo "======================================================================================================"
        echo " Please install java on your system using your favourite package manager."
        echo ""
        echo " Restart after installing java."
        echo "======================================================================================================"
        echo ""
        exit 1
    fi

    mkdir -pv "$rrupdate_archive"
    mkdir -pv "$rrupdate_bin"

    local current_version=`get_current_version`
    local latest_version=`get_latest_version`

    if [ "$current_version" != "$latest_version" ]; then
        echo "Installing version ${latest_version}"
        curl -sL ${rrupdate_url}/${latest_version}/rawrepo-update-tool-${latest_version}.jar -o ${rrupdate_archive}/rrupdate-${latest_version}.jar
        if [ $? -eq 0 ]; then
            [ -e ${rrupdate_archive}/rrupdate-current.jar ] && rm ${rrupdate_archive}/rrupdate-current.jar
            ln -s ${rrupdate_archive}/rrupdate-${latest_version}.jar ${rrupdate_archive}/rrupdate-current.jar
            unzip -o ${rrupdate_archive}/rrupdate-current.jar rrupdate.sh -d ${rrupdate_bin}
            chmod a+x ${rrupdate_bin}/rrupdate.sh
            echo ${latest_version} > ${rrupdate_home}/version
        fi

        if [ ! -f ~/.bash_aliases ]; then
            touch ~/.bash_aliases
        fi

        grep "rrupdate=~/.rrupdate/bin/rrupdate.sh" ~/.bash_aliases ||
            echo -e "\nalias rrupdate=~/.rrupdate/bin/rrupdate.sh" >> ~/.bash_aliases ; . ~/.bash_aliases
    else
        echo "Already at latest version ${latest_version}"
    fi
}

function selfupdate {
    local current_version=`get_current_version`
    local latest_version=`get_latest_version`
    if [ "$current_version" != "$latest_version" ]; then
        curl -sL ${rrupdate_url}/${latest_version}/rawrepo-update-tool-${latest_version}.jar -o /tmp/rrupdate-${latest_version}.jar
        unzip -qo /tmp/rrupdate-${latest_version}.jar rrupdate.sh -d /tmp
        bash /tmp/rrupdate.sh --install
        rm /tmp/rrupdate.sh # Clean up in case of multi user server.
    else
        echo "Already at latest version ${latest_version}"
    fi
}

function version {
    local current_version=`get_current_version`
    local latest_version=`get_latest_version`
    echo ${current_version}
    if [ "$current_version" != "$latest_version" ]; then
        echo "A new version ${latest_version} is available, update with 'rrupdate --selfupdate'"
    fi
}

case "$1" in
    --install)
    install
    ;;
    --version)
    version
    ;;
    -h)
    echo "usage: rrupdate --version"
    echo "usage: rrupdate --selfupdate"
    java -jar ${rrupdate_archive}/rrupdate-current.jar -h
    ;;
    --selfupdate)
    selfupdate
    ;;
    *)
    java -jar ${rrupdate_archive}/rrupdate-current.jar "$@"
    ;;
esac
