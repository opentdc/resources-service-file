/**
 * The MIT License (MIT)
 *
 * Copyright (c) 2015 Arbalo AG
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package org.opentdc.resources.file;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Logger;

import javax.servlet.ServletContext;

import org.opentdc.file.AbstractFileServiceProvider;
import org.opentdc.resources.ResourceModel;
import org.opentdc.resources.ServiceProvider;
import org.opentdc.service.exception.DuplicateException;
import org.opentdc.service.exception.InternalServerErrorException;
import org.opentdc.service.exception.NotFoundException;
import org.opentdc.service.exception.ValidationException;
import org.opentdc.util.PrettyPrinter;

public class FileServiceProvider extends AbstractFileServiceProvider<ResourceModel> implements ServiceProvider {

	protected static Map<String, ResourceModel> index = new HashMap<String, ResourceModel>();
	protected static final Logger logger = Logger.getLogger(FileServiceProvider.class.getName());

	public FileServiceProvider(
		ServletContext context, 
		String prefix
	) throws IOException {
		super(context, prefix);
		if (index == null) {
			index = new HashMap<String, ResourceModel>();
			List<ResourceModel> _resources = importJson();
			for (ResourceModel _resource : _resources) {
				index.put(_resource.getId(), _resource);
			}
		}
	}

	@Override
	public List<ResourceModel> listResources(
			String query, 
			String queryType,
			int position, 
			int size) 
	{
		ArrayList<ResourceModel> _resources = new ArrayList<ResourceModel>(index.values());
		Collections.sort(_resources, ResourceModel.ResourceComparator);
		ArrayList<ResourceModel> _selection = new ArrayList<ResourceModel>();
		for (int i = 0; i < _resources.size(); i++) {
			if (i >= position && i < (position + size)) {
				_selection.add(_resources.get(i));
			}			
		}
		logger.info("listResources(<" + query + ">, <" + queryType + 
				">, <" + position + ">, <" + size + ">) -> " + _selection.size() + " resources.");
		return _selection;
	}

	@Override
	public ResourceModel createResource(
		ResourceModel resource
	) throws DuplicateException, ValidationException {
		logger.info("createResource(" + PrettyPrinter.prettyPrintAsJSON(resource) + ")");
		String _id = resource.getId();
		if (_id == null || _id == "") {
			_id = UUID.randomUUID().toString();
		} else {
			if (index.get(_id) != null) {
				// object with same ID exists already
				throw new DuplicateException("resource <" + resource.getId() + 
						"> exists already.");				
			}
			else {
				throw new ValidationException("resource <" + resource.getId() +
					"> contains an ID generated on the client. This is not allowed.");
			}
		}
		if (resource.getName() == null || resource.getName().length() == 0) {
			throw new ValidationException("resource <" + resource.getId() +
					"> must have a valid name.");
		}
		if (resource.getFirstName() == null || resource.getFirstName().length() == 0) {
			throw new ValidationException("resource <" + resource.getId() +
					"> must have a valid firstName.");
		}
		if (resource.getLastName() == null || resource.getLastName().length() == 0) {
			throw new ValidationException("resource <" + resource.getId() +
					"> must have a valid lastName.");
		}
		if (resource.getContactId() == null || resource.getContactId().length() == 0) {
			throw new ValidationException("resource <" + resource.getId() +
					"> must have a valid contactId.");
		}
		resource.setId(_id);
		Date _date = new Date();
		resource.setCreatedAt(_date);
		resource.setCreatedBy("DUMMY_USER");
		resource.setModifiedAt(_date);
		resource.setModifiedBy("DUMMY_USER");	
		index.put(_id, resource);
		logger.info("createResource() -> " + PrettyPrinter.prettyPrintAsJSON(resource));
		if (isPersistent) {
			exportJson(index.values());
		}
		return resource;
	}

	@Override
	public ResourceModel readResource(
			String id) 
			throws NotFoundException {
		ResourceModel _resource = index.get(id);
		if (_resource == null) {
			throw new NotFoundException("no resource with ID <" + id
					+ "> was found.");
		}
		logger.info("readResource(" + id + ") -> " + _resource);
		return _resource;
	}

	@Override
	public ResourceModel updateResource(
		String id,
		ResourceModel resource
	) throws NotFoundException, ValidationException 
	{
		ResourceModel _rm = index.get(id);
		if(_rm == null) {
			throw new NotFoundException("resource <" + id + "> was not found.");
		} 
		if (! _rm.getCreatedAt().equals(resource.getCreatedAt())) {
			logger.warning("resource<" + id + ">: ignoring createdAt value <" + resource.getCreatedAt().toString() +
					"> because it was set on the client.");
		}
		if (!_rm.getCreatedBy().equalsIgnoreCase(resource.getCreatedBy())) {
			logger.warning("resource<" + id + ">: ignoring createdBy value <" + resource.getCreatedBy() +
					"> because it was set on the client.");
		}
		_rm.setName(resource.getName());
		_rm.setFirstName(resource.getFirstName());
		_rm.setLastName(resource.getLastName());
		_rm.setContactId(resource.getContactId());
		_rm.setModifiedAt(new Date());
		_rm.setModifiedBy("DUMMY_USER");
		index.put(id, _rm);
		logger.info("updateResource(" + id + ") -> " + PrettyPrinter.prettyPrintAsJSON(_rm));
		if (isPersistent) {
			exportJson(index.values());
		}
		return _rm;
	}

	@Override
	public void deleteResource(
			String id) 
		throws NotFoundException, InternalServerErrorException {
		ResourceModel _resource = index.get(id);
		if (_resource == null) {
			throw new NotFoundException("resource (" + id
					+ ") was not found.");
		}
		if (index.remove(id) == null) {
			throw new InternalServerErrorException("resource <" + id
					+ "> can not be removed, because it does not exist in the index");
		}
		logger.info("deleteResource(" + id + ")");
		if (isPersistent) {
			exportJson(index.values());
		}
	}
}
