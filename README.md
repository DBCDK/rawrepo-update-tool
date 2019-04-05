# Rawrepo Update Tool
Rawrepo update tool is a command line tool for sending all records from input or a file to updateservice.

## Installation
```bash
$ curl -sL http://mavenrepo.dbc.dk/content/repositories/releases/dk/dbc/rawrepo-update-tool/1.0.0/rawrepo-dump-tool-1.0.0.jar -o rrupdate.jar && unzip -op rrupdate.jar rrupdate.sh | bash -s -- --install
$ source ~/.bashrc # Or log out and into a new session
```

Keep the installation up-to-date using the selfupdate action
```bash
rrupdate --selfupdate
```

## Usage
```bash
usage: rawrepo-update-tool [-h] [-nu USERNAME] [-ng GROUP_ID] [-np PASSWORD] [-t TEMPLATE] [-tr TRACKING_ID] [-l ERROR_LIMIT] [-pi PRIORITY] [-po PROVIDER] -u URL [--validate-only [{true,false}]] IN

Send all records from file to updateservice.

positional arguments:
  IN                     Input file or standard input if given as a dash (-). 
                         Supported formats are MARCXCHANGE, LINE or ISO2709.

optional arguments:
  -h, --help             show this help message and exit
  -nu USERNAME, --username USERNAME
                         The username in the netpunkt triple used when calling update. Default 'netpunkt'.
  -ng GROUP_ID, --group-id GROUP_ID
                         The group in the netpunkt triple used when calling update. Default '010100'.
  -np PASSWORD, --password PASSWORD
                         The password in the netpunkt triple used when calling update. Default '2****r'.
  -t TEMPLATE, --template TEMPLATE
                         The template to use when calling update. Default 'dbc'.
  -tr TRACKING_ID, --tracking-id TRACKING_ID
                         The tracking id to use when calling update. No default tracking id.
  -l ERROR_LIMIT, --error-limit ERROR_LIMIT
                         The max amount of errors from updateservice before aborting the job. 
                         0 means the job is aborted on the first error. -1 means all errors are ignored. Default is 100.
  -pi PRIORITY, --priority PRIORITY
                         The priority to use when calling update. Default '1000'.
                         WARNING: Setting priority to 500 or lower will most likely affect DBCkat/Cicero.
  -po PROVIDER, --provider PROVIDER
                         Override provider to use when calling update. If not defined updateservice chooses the provider.
  -u URL, --url URL      Url of the update service of the destination rawrepo. 
                         E.g. http://oss-services.dbc.dk/UpdateService/2.0
  --validate-only [{true,false}]
                         Used to specify that the record should only be validated and not actually updated. 
                         Default true, so must be set to false in order to actually update the records.


```
## Examples
```bash
cat records.xml | rrupdate -u http://oss-services.dbc.dk/UpdateService/2.0 -
```

```bash
cat records.xml | rrupdate -nu netpunkt-user -np netpunkt-password -ng netpunkt-group -t book -tr imported-by-rrupdate -u http://oss-services.dbc.dk/UpdateService/2.0 -
```

