
// DO NOT EDIT THIS BLOCK === configuration starts ===
procedure 'CreateConfiguration', description: 'Creates a plugin configuration', {

    step 'checkConnectionGeneric',
        command: new File(pluginDir, "dsl/procedures/CreateConfiguration/steps/checkConnectionGeneric.groovy").text,
        errorHandling: 'abortProcedure',
        shell: 'ec-groovy',

        condition: '$[/javascript myJob.checkConnection == "true"]'

    step 'createConfiguration',
        command: new File(pluginDir, "dsl/procedures/CreateConfiguration/steps/createConfiguration.pl").text,
        errorHandling: 'abortProcedure',
        exclusiveMode: 'none',
        postProcessor: '$[/myProject/perl/postpLoader]',
        releaseMode: 'none',
        shell: 'ec-perl',
        timeLimitUnits: 'minutes'

    property 'ec_checkConnection', value: ''
// DO NOT EDIT THIS BLOCK === configuration ends, checksum: 5acd87f05880db100e54ebd28e59be60 ===
}