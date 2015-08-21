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

import org.opentdc.addressbooks.ContactModel;
import org.opentdc.file.AbstractFileServiceProvider;
import org.opentdc.rates.RateModel;
import org.opentdc.resources.RateRefModel;
import org.opentdc.resources.RatedResource;
import org.opentdc.resources.ResourceModel;
import org.opentdc.resources.ServiceProvider;
import org.opentdc.service.exception.DuplicateException;
import org.opentdc.service.exception.InternalServerErrorException;
import org.opentdc.service.exception.NotFoundException;
import org.opentdc.service.exception.ValidationException;
import org.opentdc.util.PrettyPrinter;

/**
 * File-based (and transient) implementation of ResourcesService.
 * @author Bruno Kaiser
 *
 */
public class FileServiceProvider extends AbstractFileServiceProvider<RatedResource> implements ServiceProvider {
	protected static Map<String, RatedResource> index = new HashMap<String, RatedResource>();
	protected static Map<String, RateRefModel> rateRefIndex = new HashMap<String, RateRefModel>();
	protected static final Logger logger = Logger.getLogger(FileServiceProvider.class.getName());

	/**
	 * Constructor.
	 * @param context the servlet context (for config)
	 * @param prefix the directory name where the seed and data.json reside (typically the classname of the service)
	 * @throws IOException
	 */
	public FileServiceProvider(
		ServletContext context, 
		String prefix
	) throws IOException {
		super(context, prefix);
		if (index == null) {
			index = new HashMap<String, RatedResource>();
			List<RatedResource> _resources = importJson();
			for (RatedResource _resource : _resources) {
				index.put(_resource.getModel().getId(), _resource);
			}
		}
	}

	/* (non-Javadoc)
	 * @see org.opentdc.resources.ServiceProvider#listResources(java.lang.String, java.lang.String, int, int)
	 */
	@Override
	public List<ResourceModel> listResources(
			String query, 
			String queryType,
			int position, 
			int size) 
	{
		ArrayList<ResourceModel> _resources = new ArrayList<ResourceModel>();
		for (RatedResource _ratedRes : index.values()) {
			_resources.add(_ratedRes.getModel());
		}
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

	/* (non-Javadoc)
	 * @see org.opentdc.resources.ServiceProvider#createResource(org.opentdc.resources.ResourceModel)
	 */
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
		resource.setId(_id);
		
		if (resource.getName() == null || resource.getName().length() == 0) {
			throw new ValidationException("resource <" + _id +
					"> must have a valid name.");
		}
		if (resource.getContactId() == null || resource.getContactId().length() == 0) {
			throw new ValidationException("resource <" + _id +
					"> must have a valid contactId.");
		}
		ContactModel _contactModel = getContactModel(resource.getContactId());
		
		if (resource.getFirstName() != null && !resource.getFirstName().isEmpty()) {
			logger.warning("resource <" + _id +  
					">: firstName is a derived field and will be overwritten.");
		}
		resource.setFirstName(_contactModel.getFirstName());
		
		if (resource.getLastName() != null && !resource.getLastName().isEmpty()) {
			logger.warning("resource <" + _id +  
					">: lastName is a derived field and will be overwritten.");
		}
		resource.setLastName(_contactModel.getLastName());
		Date _date = new Date();
		resource.setCreatedAt(_date);
		resource.setCreatedBy(getPrincipal());
		resource.setModifiedAt(_date);
		resource.setModifiedBy(getPrincipal());
		RatedResource _ratedRes = new RatedResource();
		_ratedRes.setModel(resource);
		index.put(_id, _ratedRes);
		logger.info("createResource() -> " + PrettyPrinter.prettyPrintAsJSON(resource));
		if (isPersistent) {
			exportJson(index.values());
		}
		return resource;
	}
	
	private ContactModel getContactModel(
			String contactId) {
		return org.opentdc.addressbooks.file.FileServiceProvider.getContactModel(contactId);
	}


	/* (non-Javadoc)
	 * @see org.opentdc.resources.ServiceProvider#readResource(java.lang.String)
	 */
	@Override
	public ResourceModel readResource(
			String resourceId) 
			throws NotFoundException {
		ResourceModel _resource = readRatedResource(resourceId).getModel();
		logger.info("readResource(" + resourceId + ") -> " + _resource);
		return _resource;
	}
	
	/**
	 * Retrieve the RatedResource based on the resourceId.
	 * @param resourceId the unique id of the resource
	 * @return the RatedResource that contains the resource as its model
	 * @throws NotFoundException if no resource with this id was found
	 */
	private static RatedResource readRatedResource(
			String resourceId) 
					throws NotFoundException 
	{
		RatedResource _ratedRes = index.get(resourceId);
		if (_ratedRes == null) {
			throw new NotFoundException("no resource with id <" + resourceId + "> was found.");
		}
		logger.info("readRatedResource(" + resourceId + ") -> " + PrettyPrinter.prettyPrintAsJSON(_ratedRes));
		return _ratedRes;
	}
	
	/**
	 * Retrieve a ResourceModel by its ID.
	 * @param id the ID of the resource
	 * @return the resource found
	 * @throws NotFoundException if not resource with such an ID was found
	 */
	public static ResourceModel getResourceModel(
			String id) 
			throws NotFoundException {
		return readRatedResource(id).getModel();
	}
	
	/* (non-Javadoc)
	 * @see org.opentdc.resources.ServiceProvider#updateResource(java.lang.String, org.opentdc.resources.ResourceModel)
	 */
	@Override
	public ResourceModel updateResource(
		String id,
		ResourceModel resource
	) throws NotFoundException, ValidationException 
	{
		RatedResource _ratedRes = readRatedResource(id);
		ResourceModel _resModel = _ratedRes.getModel();
		if (! _resModel.getCreatedAt().equals(resource.getCreatedAt())) {
			logger.warning("resource<" + id + ">: ignoring createdAt value <" + resource.getCreatedAt().toString() +
					"> because it was set on the client.");
		}
		if (!_resModel.getCreatedBy().equalsIgnoreCase(resource.getCreatedBy())) {
			logger.warning("resource<" + id + ">: ignoring createdBy value <" + resource.getCreatedBy() +
					"> because it was set on the client.");
		}
		ContactModel _contactModel = getContactModel(resource.getContactId());
		_resModel.setContactId(resource.getContactId());
		
		if (resource.getFirstName() != null && !resource.getFirstName().isEmpty()) {
			logger.warning("resource <" + id +  
					">: firstName is overwritten because it is a derived field.");
		}
		_resModel.setFirstName(_contactModel.getFirstName());
		
		if (resource.getLastName() != null && !resource.getLastName().isEmpty()) {
			logger.warning("resource <" + id +  
					">: lastName is overwritten because it is a derived field.");
		}
		_resModel.setLastName(_contactModel.getLastName());
		_resModel.setName(resource.getName());
		_resModel.setModifiedAt(new Date());
		_resModel.setModifiedBy(getPrincipal());
		_ratedRes.setModel(_resModel);
		index.put(id, _ratedRes);
		logger.info("updateResource(" + id + ") -> " + PrettyPrinter.prettyPrintAsJSON(_resModel));
		if (isPersistent) {
			exportJson(index.values());
		}
		return _resModel;
	}

	/* (non-Javadoc)
	 * @see org.opentdc.resources.ServiceProvider#deleteResource(java.lang.String)
	 */
	@Override
	public void deleteResource(
			String id) 
		throws NotFoundException, InternalServerErrorException {
		RatedResource _ratedRes = readRatedResource(id);
		// remove all rateRefs of this ratedRes from rateRefIndex
		for (RateRefModel _rateRef : _ratedRes.getRateRefs()) {
			if (rateRefIndex.remove(_rateRef.getId()) == null) {
				throw new InternalServerErrorException("rateRef <" + _rateRef.getId()
						+ "> can not be removed, because it does not exist in the rateRefIndex");				
			}
		}
		if (index.remove(id) == null) {
			throw new InternalServerErrorException("resource <" + id
					+ "> can not be removed, because it does not exist in the index");
		}
		logger.info("deleteResource(" + id + ") -> OK");
		if (isPersistent) {
			exportJson(index.values());
		}
	}
	
	/************************************** RateRef ************************************/
	/* (non-Javadoc)
	 * @see org.opentdc.resources.ServiceProvider#listRateRefs(java.lang.String, java.lang.String, java.lang.String, int, int)
	 */
	@Override
	public List<RateRefModel> listRateRefs(
			String resourceId, 
			String query,
			String queryType, 
			int position, 
			int size) {
		List<RateRefModel> _rates = readRatedResource(resourceId).getRateRefs();
		Collections.sort(_rates, RateRefModel.RatesRefComparator);
		
		ArrayList<RateRefModel> _selection = new ArrayList<RateRefModel>();
		for (int i = 0; i < _rates.size(); i++) {
			if (i >= position && i < (position + size)) {
				_selection.add(_rates.get(i));
			}
		}
		logger.info("listRateRefs(<" + resourceId + ">, <" + queryType + ">, <" + query + 
				">, <" + position + ">, <" + size + ">) -> " + _selection.size()
				+ " values");
		return _selection;
	} 
	
	/**
	 * Retrieve a rate by ID
	 * @param rateId the id of the rate to look for
	 * @return the rate found
	 */
	private RateModel getRateModel(
			String rateId) {
		return org.opentdc.rates.file.FileServiceProvider.getRatesModel(rateId);
		/*
		 * better solution would be by calling the service:
		// check that the rateId is valid and derive the title
		WebClient _webClient = ServiceUtil.createWebClient(ServiceUtil.RATES_API_URL, RatesService.class);
		_webClient.type(MediaType.APPLICATION_JSON).accept(MediaType.APPLICATION_JSON);
		Response _response = _webClient.replacePath("/").path(rateId).get();
		int _status = _response.getStatus();
		if (_status != Status.OK.getStatusCode()) {
			if (_status == Status.NOT_FOUND.getStatusCode()) {
				throw new ValidationException("referenced rate <" + 
						rateId + "> does not exist; please create it first.");				
			} else {
				throw new ValidationException("Call to RatesService returned with HTTP status " + _status);
			}
		}
		//see http://stackoverflow.com/questions/23656538/nosuchmethoderror-with-cxf-response-object
		RatesModel _model = null;
		if (_response instanceof org.apache.cxf.jaxrs.impl.ResponseImpl) {
			_model = ((org.apache.cxf.jaxrs.impl.ResponseImpl) _response).readEntity(RatesModel.class);
		} else {
			_model = _response.readEntity(RatesModel.class);
		}
		return _model;
		*/
	}
	
	/* (non-Javadoc)
	 * @see org.opentdc.resources.ServiceProvider#createRateRef(java.lang.String, org.opentdc.resources.RateRefModel)
	 */
	@Override
	public RateRefModel createRateRef(
			String resourceId, 
			RateRefModel model)
			throws DuplicateException, ValidationException 
	{
		RatedResource _ratedRes = readRatedResource(resourceId);
		if (model.getRateId() == null || model.getRateId().isEmpty()) {
			throw new ValidationException("RateRef in Resource <" + resourceId + "> must contain a valid rateId.");
		}
		if (model.getRateTitle() != null && !model.getRateTitle().isEmpty()) {
			logger.warning("RateRef in Resource <" + resourceId +  
					">: title is a derived field and will be overwritten.");
		}
		// a rate can be contained as a RateRef within Resource 0 or 1 times
		if (_ratedRes.containsRate(model.getRateId())) {
			throw new DuplicateException("RateRef with Rate <" + model.getRateId() + 
					"> exists already in Resource <" + resourceId + ">.");
		}

		model.setRateTitle(getRateModel(model.getRateId()).getTitle());
		
		String _id = model.getId();
		if (_id == null || _id.isEmpty()) {
			_id = UUID.randomUUID().toString();
		} else {
			if (rateRefIndex.get(_id) != null) {
				throw new DuplicateException("RateRef with id <" + _id + 
						"> exists already in rateRefIndex.");
			}
			else {
				throw new ValidationException("RateRef with id <" + _id +
						"> contains an ID generated on the client. This is not allowed.");
			}
		}

		model.setId(_id);
		model.setCreatedAt(new Date());
		model.setCreatedBy(getPrincipal());
		
		rateRefIndex.put(_id, model);
		_ratedRes.addRateRef(model);
		
		logger.info("createRateRef(" + resourceId + ") -> " + PrettyPrinter.prettyPrintAsJSON(model));
		if (isPersistent) {
			exportJson(index.values());
		}
		return model;
	}

	/* (non-Javadoc)
	 * @see org.opentdc.resources.ServiceProvider#readRateRef(java.lang.String, java.lang.String)
	 */
	@Override
	public RateRefModel readRateRef(
			String resourceId, 
			String rateRefId)
			throws NotFoundException {
		readRatedResource(resourceId);		// verify that the resource exists
		RateRefModel _rateRef = rateRefIndex.get(rateRefId);
		if (_rateRef == null) {
			throw new NotFoundException("RateRef <" + resourceId + "/rateref/" + rateRefId +
					"> was not found.");
		}
		logger.info("readRateRef(" + resourceId + ", " + rateRefId + ") -> "
				+ PrettyPrinter.prettyPrintAsJSON(_rateRef));
		return _rateRef;
	}

	/* (non-Javadoc)
	 * @see org.opentdc.resources.ServiceProvider#deleteRateRef(java.lang.String, java.lang.String)
	 */
	@Override
	public void deleteRateRef(
			String resourceId, 
			String rateRefId)
			throws NotFoundException, InternalServerErrorException {
		RatedResource _ratedRes = readRatedResource(resourceId);
		RateRefModel _rateRef = rateRefIndex.get(rateRefId);
		if (_rateRef == null) {
			throw new NotFoundException("RateRef <" + resourceId + "/rateref/" + rateRefId +
					"> was not found.");
		}
		
		// 1) remove the RateRef from its Resource
		if (_ratedRes.removeRateRef(_rateRef) == false) {
			throw new InternalServerErrorException("RateRef <" + resourceId + "/rateref/" + rateRefId
					+ "> can not be removed, because it is an orphan.");
		}
		// 2) remove the RateRef from the rateRefIndex
		if (rateRefIndex.remove(_rateRef.getId()) == null) {
			throw new InternalServerErrorException("RateRef <" + resourceId + "/rateref/" + rateRefId
					+ "> can not be removed, because it does not exist in the index.");
		}	
		logger.info("deleteRateRef(" + resourceId + ", " + rateRefId + ") -> OK");
		if (isPersistent) {
			exportJson(index.values());
		}		
	}
}
