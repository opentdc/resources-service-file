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

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Logger;

import javax.servlet.ServletContext;

import org.opentdc.resources.ResourceModel;
import org.opentdc.resources.ServiceProvider;
import org.opentdc.service.exception.DuplicateException;
import org.opentdc.service.exception.NotFoundException;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

public class FileServiceProvider implements ServiceProvider {
	private static final String SEED_FN = "/seed.json";
	private static final String DATA_FN = "/data.json";
	private static File dataF = null;
	private static File seedF = null;

	// instance variables
	private boolean isPersistent = true;

	protected static Map<String, ResourceModel> index = new HashMap<String, ResourceModel>();

	// instance variables
	protected static final Logger logger = Logger.getLogger(FileServiceProvider.class.getName());

	public FileServiceProvider(
		ServletContext context, 
		String prefix
	) {
		if (dataF == null) {
			dataF = new File(context.getRealPath("/" + prefix + DATA_FN));
		}
		if (seedF == null) {
			seedF = new File(context.getRealPath("/" + prefix + SEED_FN));
		}
		if (index.size() == 0) {
			importJson();
		}
	}

	@Override
	public ArrayList<ResourceModel> listResources(
		String queryType,
		String query,
		int position,
		int size
	) {
		logger.info("listResources() -> " + countResources() + " values");
		return new ArrayList<ResourceModel>(index.values());
	}

	@Override
	public ResourceModel createResource(
		ResourceModel resource
	) throws DuplicateException {
		logger.info("createResource(" + resource + ")");
		String _id = resource.getId();
		if (_id == null || _id == "") {
			_id = UUID.randomUUID().toString();
		} else {
			if (index.get(_id) != null) {
				// object with same ID exists already
				throw new DuplicateException();				
			}
		}
		ResourceModel _resource = new ResourceModel(
				resource.getFirstName() + " " + resource.getLastName(),
				resource.getFirstName(), 
				resource.getLastName());
		_resource.setId(_id);
		index.put(_id, _resource);
		if (isPersistent) {
			exportJson(dataF);
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
				exportJson(dataF);
			}
			return resource;
		}
	}

	@Override
	public void deleteResource(String id) throws NotFoundException {
		ResourceModel _resource = index.get(id);
		;
		if (_resource == null) {
			throw new NotFoundException("deleteResource(" + id
					+ "): no such resource was found.");
		}
		index.remove(id);
		logger.info("deleteResource(" + id + ")");
		if (isPersistent) {
			exportJson(dataF);
		}
	}

	@Override
	public int countResources() {
		int _count = -1;
		if (index != null) {
			_count = index.values().size();
		}
		logger.info("countResources() = " + _count);
		return _count;
	}

	void importJson() {
		ArrayList<ResourceModel> _resources = null;

		// read the data file
		// either read persistent data from DATA_FN
		// or seed data from SEED_DATA_FN if no persistent data exists
		if (dataF.exists()) {
			logger.info("persistent data in file " + dataF.getName()
					+ " exists.");
			_resources = importJson(dataF);
		} else { // seeding the data
			logger.info("persistent data in file " + dataF.getName()
					+ " is missing -> seeding from " + seedF.getName());
			_resources = importJson(seedF);
		}
		// load the data into the local transient storage
		for (ResourceModel _resource : _resources) {
			index.put(_resource.getId(), _resource);
		}
		logger.info("added " + _resources.size() + " resources to index");

		if (isPersistent) {
			// create the persistent data if it did not exist
			if (!dataF.exists()) {
				try {
					dataF.createNewFile();
				} catch (IOException e) {
					logger.severe("importJson(): IO exception when creating file "
							+ dataF.getName());
					e.printStackTrace();
				}
				exportJson(dataF);
			}
		}
		logger.info("importJson(): imported " + _resources.size() + " resource objects");
	}

	/******************************** utility methods *****************************************/
	private ArrayList<ResourceModel> importJson(File f) throws NotFoundException {
		logger.info("importJson(" + f.getName() + "): importing ResourcesData");
		if (!f.exists()) {
			logger.severe("importJson(" + f.getName()
					+ "): file does not exist.");
			throw new NotFoundException("File " + f.getName()
					+ " does not exist.");
		}
		if (!f.canRead()) {
			logger.severe("importJson(" + f.getName()
					+ "): file is not readable");
			throw new NotFoundException("File " + f.getName()
					+ " is not readable.");
		}
		logger.info("importJson(" + f.getName() + "): can read the file.");

		Reader _reader = null;
		ArrayList<ResourceModel> _resources = null;
		try {
			_reader = new InputStreamReader(new FileInputStream(f));
			Gson _gson = new GsonBuilder().create();

			Type _collectionType = new TypeToken<ArrayList<ResourceModel>>() {
			}.getType();
			_resources = _gson.fromJson(_reader, _collectionType);
			logger.info("importJson(" + f.getName() + "): json data converted");
		} catch (FileNotFoundException e1) {
			logger.severe("importJson(" + f.getName()
					+ "): file does not exist (2).");
			e1.printStackTrace();
		} finally {
			try {
				if (_reader != null) {
					_reader.close();
				}
			} catch (IOException e) {
				logger.severe("importJson(" + f.getName()
						+ "): IOException when closing the reader.");
				e.printStackTrace();
			}
		}
		logger.info("importJson(" + f.getName() + "): " + _resources.size()
				+ " resources imported.");
		return _resources;
	}

	private void exportJson(File f) {
		logger.info("exportJson(" + f.getName() + "): exporting resources");
		Writer _writer = null;
		try {
			_writer = new OutputStreamWriter(new FileOutputStream(f));
			Gson _gson = new GsonBuilder().create();
			_gson.toJson(index.values(), _writer);
		} catch (FileNotFoundException e) {
			logger.severe("exportJson(" + f.getName() + "): file not found.");
			e.printStackTrace();
		} finally {
			if (_writer != null) {
				try {
					_writer.close();
				} catch (IOException e) {
					logger.severe("exportJson(" + f.getName()
							+ "): IOException when closing the reader.");
					e.printStackTrace();
				}
			}
		}
	}

}
