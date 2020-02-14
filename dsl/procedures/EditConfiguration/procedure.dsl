
// DO NOT EDIT THIS BLOCK === configuration starts ===
// This part is auto-generated and will be regenerated upon subsequent updates
procedure 'EditConfiguration', description: 'Checks connection for the changed configuration', {

    step 'checkConnectionGeneric',
        command: new File(pluginDir, "dsl/procedures/CreateConfiguration/steps/checkConnectionGeneric.groovy").text,
        errorHandling: 'abortProcedure',
        shell: 'ec-groovy',
        postProcessor: '$[/myProject/perl/postpLoader]',
        condition: '$[/javascript myJob.checkConnection == "true" || myJob.checkConnection == "1"]'

}
// DO NOT EDIT THIS BLOCK === configuration ends, checksum: 6d1a7524af815195d208ff71f623eb77 ===
