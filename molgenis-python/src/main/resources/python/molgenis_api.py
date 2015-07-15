###################################################################
#
# Molgenis python api client. 
#
# TODO: Better error raising
####################################################################

import requests
import json
import warnings
import re

class Connect_Molgenis():
    """Some simple methods for adding, updating and retrieving rows from Molgenis though the REST API

    Args:
          server_url (string): The url to the molgenis server
          user (string):       Login username
          password (string):   Login password

    Example:
        from molgenis_api import molgenis
        # make a connection
        connection = connection = molgenis.Connect_Molgenis()('http://localhost:8080', 'admin', 'admin')
        # add a row to the entity public_rnaseq_Individuals
        connection.add_entity_row('public_rnaseq_Individuals',{'id':'John Doe','age':'26', 'gender':'Male'})
        # get the rows from public_rnaseq_Individuals where gender = Male
        print connection.query_entity_rows('public_rnaseq_Individuals',[{'field':'gender', 'operator':'EQUALS', 'value':'Male'}])['items'] 
        # update row in public_rnaseqIndivduals where id=John Doe -> set gender to Female
        connection.update_entity_row('public_rnaseq_Individuals',[{'field':'id', 'operator':'EQUALS', 'value':'John Doe'}], {'gender':'Female'})  
    """

    def __init__(self, server_url, user, password, verbose = True, give_warnings = True):
        '''Initialize Python api to talk to Molgenis Rest API
        
        Args:
            server_url (string): The url to the molgenis server (ex: https://molgenis39.target.rug.nl/)
            user (string):       Login username
            password (string):   Login password
            verbose (bool):      If True, print out extra info, if False no prints are used (def: True)
            warnings (bool):     If True, warnings are given for certain operations where something might have gone wrong, but not sure enough to raise exception.
        '''
        self.verbose = verbose
        self.api_url = server_url+'/api/v1'
        self.headers = self._construct_login_header(user, password)
        self.entity_meta_data = {}
        self.column_meta_data = {}
        self.give_warnings = give_warnings
        self.server_response_json = None
        self.last_added_id = None
        
    def get_last_added_id(self):
        return self.last_added_id
    
    def set_verbosity(self, verbose):
        '''Set verbosity on or off
        
        Args:
            verbose (bool): True sets it on, False sets it off.
        '''
        self.verbose = verbose
    
    def set_give_warnings(self, give_warnings):
        '''Set give_warnings on or off
        
        Args:
            give_warnings (bool): True sets it on, False sets it off.
        '''
        self.give_warnings = give_warnings
    
    def _construct_login_header(self, user, password):
        '''Log in to the molgenis server and use the retrieve loginResponse token to construct the login header.
         
        Args:
            user (string): Login username
            password (string): Login password
    
        Returns:
            header (dict): Login header for molgenis server
        '''
        data = json.dumps({'username': user, 'password': password})
                                       
        server_response = requests.post( self.api_url+'/login/',
                                       data=data, headers={'Content-type':'application/json'} )
        self.check_server_response(server_response, 'retrieve token',data_used=data)
        self.server_response_json = server_response.json()['token']
        headers = {'Content-type':'application/json', 'x-molgenis-token': self.server_response_json, 'Accept':'application/json'}
        return headers
    
    def logout(self):
        server_response = requests.get(self.api_url+'/logout/', headers=self.headers)
        self.check_server_response(server_response, 'logout')
        return server_response
    
    def check_server_response(self, server_response, type_of_request, entity_used=None, data_used=None,query_used=None,column_used=None):
        '''Retrieve error message from server response
        
        Args:
            server_response (server response object): Response from server
            type_of_request (string): Extra info to print with verbose print (def: '')
        Returns:
            True if response 200 or 201, False if other response, raises error if 400
            
        Raises:
            BaseException: if json object of server response contains error messages 
        '''
        def error(server_response):
            try:
                self.server_response_json = server_response.json()
                error_message = str(server_response)+' -> '+server_response.reason+'\n'
                if self.server_response_json.has_key('errors'):
                    if data_used:
                        error_message += 'Used data: '+str(data_used)+'\n'
                    if entity_used:
                        error_message += 'Used Entity: '+str(entity_used)+'\n'
                    if query_used:
                        error_message += 'Used Query: '+str(query_used)+'\n'
                    if column_used:
                        error_message += 'Used column: '+str(column_used)+'\n'
                    for error in self.server_response_json['errors']:
                        error_message += error['message']+'\n'
                        if 'Not Found' in error_message:
                            if entity_used: 
                                error_message += 'Available columns for entity \''+entity_used+'\': '+', '.join(self.get_column_names(entity_used))
                        # bug in error response when wrong enum value. Remove wrong part of message and add sensible one
                        if 'Invalid enum value' in error_message:
                            column_name = re.search('for attribute \'(.+?)\'', error_message).group(1)
                            entity_name = re.search('of entity \'(.+?)\'', error_message).group(1)
                            column_meta = self.get_column_meta_data(entity_name,column_name)
                            enum_options = ', '.join(column_meta['enumOptions'])
                            error_message = error_message.replace('Value must be less than or equal to 255 characters',
                                                  ' The enum options are: '+enum_options)
                        raise BaseException(error_message.rstrip('\n'))
            except ValueError:
                pass # no json oobject in server_response
        if str(server_response) == '<Response [400]>' or str(server_response) == '<Response [404]>':
            error(server_response) 
        elif str(server_response) == '<Response [200]>' or str(server_response) == '<Response [201]>' or str(server_response) == '<Response [204]>':
            if self.verbose:
                print type_of_request+' -> '+str(server_response)+' - '+server_response.reason
            return True
        else:
            error(server_response)
            # i didn't go through all response codes, so if it's a different response than expected I only want to raise exception if 
            # there are error messages in the response object, otherwise just warn that there is a different response than expected
            if self.give_warnings:
                warnings.warn('Expected <Response [200]>, <Response 201> or <Response 204>, got '+str(server_response)+'\nReason: '+server_response.reason)
            return False
    
    def validate_data(self, entity_name, data):
        '''Validate that the right column names are given, since otherwise if wrong columns are used it will create empty rows. 
        Only the column names and auto ID get checked, not value type, as server will raise an error if wrong type is tried to be inserted
        
        Args:
            data (dict): Dictonary that will be used as json_data
            
        Raises:
            BaseException
        '''
        columns_to_insert = data.keys()
        columns_in_entity = self.get_column_names(entity_name)
        difference = set(columns_to_insert).difference(set(columns_in_entity))
        if len(difference) > 0:
            error_message = 'Provided data has columns which are not in the entity. The wrong columns are: '+', '.join(difference)+'\n'\
                           +'The provided data is: '+str(data)+'\n'\
                           +'The entity '+entity_name+' contains the columns: '+', '.join(columns_in_entity)
            raise BaseException(error_message)
        entity_id_attribute = self.get_id_attribute(entity_name)
        if entity_id_attribute in data and self.get_column_meta_data(entity_name,entity_id_attribute)['auto']:
            if self.give_warnings:
                warnings.warn('The ID attribute ('+entity_id_attribute+') of the entity ('+entity_name+') you are adding a row to is set to `auto`.\n'\
                             +'The value you gave for id ('+data[entity_id_attribute]+') will not be used. Instead, the ID will be a random string.')
        
    def add_entity_row(self, entity_name, data, validate_json=True):
        '''Add a row to an entity
        
        Args:
            entity_name (string): Name of the entity where row should be added
            json_data (dict): Key = column name, value = column value
            validate_json (bool): If True, check if the given data keys correspond with the column names of entity_name.
                              If adding entity rows seems slow, try setting to False (def: True)
                              
        Returns:
            server_response (Response object): Response from server
        '''
        # make a string of json data (dictionary) with key=column name and value=value you want (works for 1 individual, Jonatan is going to find out how to to it with multiple)
        # post to the entity with the json data
        if validate_json:
            self.validate_data(entity_name, data)
        self.server_response = requests.post(self.api_url+'/'+entity_name+'/', data=str(data), headers=self.headers)
        self.check_server_response(self.server_response, 'Add row to entity', entity_used=entity_name, data_used=str(data))
        self.last_added_id = self.server_response.headers['location']
        return self.server_response

    def query_entity_rows(self, entity_name, query):
        '''Get row(s) from entity with a query
        
        Args:
            entity_name (string): Name of the entity where get query should be run on
            query (list): List of dictionaries with as keys:values -> [{'field':column name, 'operator':'EQUALS', 'value':value}]
             
        Returns:
            result (dict): json dictionary of retrieve data
        
        TODO:
            More difficult get queries
        '''
        if len(query) == 0:
            raise ValueError('Can\'t search with empty query')
        json_query = json.dumps({'q':query})
        self.server_response = requests.post(self.api_url+'/'+entity_name+'?_method=GET', data = json_query, headers=self.headers)
        self.server_response_json = self.server_response.json()
        self.check_server_response(self.server_response, 'Get rows from entity',entity_used=entity_name, query_used=json_query)
        if self.verbose:
            if self.server_response_json['total'] >= self.server_response_json['num']:
                if self.give_warnings:
                    warnings.warn(str(self.server_response_json['total'])+' number of rows selected. Max number of rows to retrieve data for is set to '+str(self.server_response_json['num'])+'.\n'
                                 +str(self.server_response_json['num']-self.server_response_json['total'])+' rows will not be in the results.')
            print 'Selected '+str(self.server_response_json['total'])+' row(s).'
        return self.server_response_json
  
    def get_entity(self, entity_name):
        '''Get all data of entity_name
        
        Args:
            entity_name (string): Name of the entity where get query should be run on
            query (list): List of dictionaries with as keys:values -> [{'field':column name, 'operator':'EQUALS', 'value':value}]
             
        Returns:
            result (dict): json dictionary of retrieve data
        
        TODO:
            More difficult get queries
        '''
        self.server_response = requests.get(self.api_url+'/'+entity_name, headers=self.headers)
        self.server_response_json = self.server_response.json()
        self.check_server_response(self.server_response, 'Get rows from entity',entity_used=entity_name)
        if self.verbose:
            if self.server_response_json['total'] >= self.server_response_json['num']:
                if self.give_warnings:
                    warnings.warn(str(self.server_response_json['total'])+' number of rows selected. Max number of rows to retrieve data for is set to '+str(self.server_response_json['num'])+'.\n'
                                 +str(int(self.server_response_json['num'])-int(self.server_response_json['total']))+' rows will not be in the results.')
            print 'Selected '+str(self.server_response_json['total'])+' row(s).'
        return self.server_response_json

  
    def update_entity_rows(self, entity_name, query, data):
        '''Update an entity row
    
        Args:
            entity_name (string): Name of the entity to update
            query (list): List of dictionaries which contain query to select the row to update (see documentation of query_entity_rows)
            data (dict):  Key = column name, value = column value
        '''
        self.validate_data(entity_name, data)
        entity_data = self.query_entity_rows(entity_name, query)
        if len(entity_data['items']) == 0:
            raise BaseException('Query returned 0 results, no row to update.')
        id_attribute = self.get_id_attribute(entity_name)
        server_response_list = [] 
        for entity_items in entity_data['items']:
            # column values that are not given will be overwritten with null, so we need to add the existing column data into our dict
            for key in entity_items:
                if key != id_attribute and key not in data:
                    data[key.encode('ascii')] = str(entity_items[key]).encode('ascii')
            row_id = entity_items[id_attribute]
            server_response = requests.put(self.api_url+'/'+entity_name+'/'+row_id+'/', data=str(data), headers=self.headers)
            server_response_list.append(server_response)
            self.check_server_response(server_response, 'Update entity row', query_used=query,data_used=data,entity_used=entity_name)
        return server_response_list
    
    def get_entity_meta_data(self, entity_name):
        '''Get metadata from entity
        
        Args:
            entity_name (string): Name of the entity to get meta data of
        
        Returns:
            result (dict): json dictionary of retrieve data
        '''
        if entity_name in self.entity_meta_data:
            return self.entity_meta_data[entity_name] 
        server_response = requests.get(self.api_url+'/'+entity_name+'/meta', headers=self.headers)
        self.check_server_response(server_response, 'Get meta data of entity',entity_used=entity_name)
        self.server_response_json = server_response.json()
        self.entity_meta_data[entity_name] = self.server_response_json
        return self.entity_meta_data

    def get_column_names(self, entity_name):
        '''Get the column names from the entity
        
        Args:
            entity_name (string): Name of the entity to get column names of
        Returns:
            meta_data(list): List with all the column names of entity_name
        '''
        entity_meta_data = self.get_entity_meta_data(entity_name)[entity_name]
        attributes = entity_meta_data['attributes']
        return attributes.keys()
    
    def get_id_attribute(self, entity_name):
        '''Get the id attribute name'''
        entity_meta_data = self.get_entity_meta_data(entity_name)[entity_name]
        return entity_meta_data['idAttribute']
    
    def get_column_meta_data(self, entity_name, column_name):
        '''Get the meta data for column_name of entity_name
        
        Args:
            entity_name (string): Name of the entity 
            column_name (string): Name of the column
        Returns:
            List with all the column names of entity_name
        '''
        if entity_name+column_name in self.column_meta_data:
            return self.column_meta_data[entity_name+column_name]
        self.server_response = requests.get(self.api_url+'/'+entity_name+'/meta/'+column_name, headers=self.headers)
        self.check_server_response(self.server_response, 'Get meta data of column',entity_used=entity_name,column_used=column_name)
        self.server_response_json = self.server_response.json()
        self.column_meta_data[entity_name+column_name] = self.server_response_json
        return self.column_meta_data
    
    def get_column_type(self, entity_name, column_name):
        column_meta_data = self.get_column_meta_data(entity_name, column_name)[entity_name+column_name]
        return column_meta_data['fieldType']
    
    def delete_all_entity_rows(self,entity_name):
        self.delete_entity_rows(entity_name, [{'field':self.get_id_attribute(entity_name),'operator':'EQUALS','value':'*'}])
        
    def delete_entity_rows(self, entity_name, query):
        '''delete entity rows
    
        Args:
            entity_name (string): Name of the entity to update
            query (list): List of dictionaries which contain query to select the row to update (see documentation of query_entity_rows)
        '''
        entity_data = self.query_entity_rows(entity_name, query)
        if len(entity_data['items']) == 0:
            raise BaseException('Query returned 0 results, no row to delete.')
        id_attribute = self.get_id_attribute(entity_name)
        server_response_list = [] 
        for rows in entity_data['items']:
            row_id = rows[id_attribute]
            self.server_response = requests.delete(self.api_url+'/'+entity_name+'/'+row_id+'/', headers=self.headers)
            self.check_server_response(self.server_response, 'Delete entity row',entity_used=entity_name,query_used=query)
            server_response_list.append(self.server_response)
        return server_response_list
