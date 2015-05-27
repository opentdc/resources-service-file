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
	public ArrayList<ResourceModel> listResources(
		String queryType,
		String query,
		int position,
		int size
	) {
		logger.info("listResources() -> " + index.size() + " values");
		return new ArrayList<ResourceModel>(index.values());
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
		ResourceModel _resource = new ResourceModel(
				resource.getName(),
				resource.getFirstName(), 
				resource.getLastName(),
				resource.getContactId());
		_resource.setId(_id);
		index.put(_id, _resource);
		logger.info("createResource() -> " + PrettyPrinter.prettyPrintAsJSON(resource));
		if (isPersistent) {
			exportJson(index.values());
		}
		return _resource;
	}

	@Override
	public ResourceModel readResource(String id) throws NotFoundException {
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
	) throws NotFoundException {
		if(index.get(id) == null) {
			throw new NotFoundException();
		} else {
			index.put(resource.getId(), resource);
			logger.info("updateResource(" + resource + ")");
			if (isPersistent) {
				exportJson(index.values());
			}
			return resource;
		}
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
