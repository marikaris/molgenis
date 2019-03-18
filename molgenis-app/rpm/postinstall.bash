#!/bin/bash

INTERACTIVE=true
MOLGENIS_HOME=/home/molgenis/

DB_USER=molgenis
DB_PASSWORD=molgenis
ADMIN_PASSWORD=
MAIL_USER=molgenis
MAIL_PASSWORD=xxxx

echo "########################################"
echo "POSTINSTALL - MOLGENIS - started"
echo "########################################"
echo "[INFO] Stop tomcat before upgrade"
service tomcat stop
echo "----------------------------------------"
echo "[INFO] Remove old installation"
rm -rf /usr/share/tomcat/webapps/ROOT*
echo "[INFO] Move the specific version to the ROOT.war"
echo "[INFO] Copy MOLGENIS war on tomcat"
echo "----------------------------------------"
mv /usr/local/share/molgenis/war/*.war /usr/local/share/molgenis/war/ROOT.war
cp -rp /usr/local/share/molgenis/war/ROOT.war /usr/share/tomcat/webapps/ROOT.war

echo "--------------------------------------------------"
echo "[INFO] Determine if there is a molgenis-server.properties"
if [[ ! -f ${MOLGENIS_HOME}/molgenis-server.properties ]]; then
  if [[ ${INTERACTIVE} -eq "true" ]]; then
    echo "[INFO] Configure [ molgenis-server.properties ]"
    echo "*****************************************"
    read -p "Enter database user: " DB_USER
    read -p "Enter database password: " DB_PASSWORD
    read -p "Enter admin password: " ADMIN_PASSWORD
    read -p "Enter mail username: " MAIL_USER
    read -p "Enter mail password: " MAIL_PASSWORD
  else
    "[INFO] Running in non-interactive mode"
    "[INFO] Generate a [ molgenis-server.properties ] file"
    sed -e "s|__DB_USER__|${DB_USER}|" \
      -e "s|__DB_PASSWORD__|${DB_PASSWORD}|" \
      -e "s|__ADMIN_PASSWORD__|${ADMIN_PASSWORD}|" \
      -e "s|__MAIL_USER__|${MAIL_USER}|" \
      -e "s|__MAIL_PASSWORD__|${MAIL_PASSWORD}|" \
      /usr/local/share/molgenis/templates/molgenis-server.properties > ${MOLGENIS_HOME}/molgenis-server.properties
  fi
else
  echo "[INFO] molgenis-server.properties already exists"
fi

echo "----------------------------------------"
echo "[INFO] Start tomcat"
service tomcat start

echo "----------------------------------------"
echo "POSTINSTALL - MOLGENIS - finished"
echo "########################################"
