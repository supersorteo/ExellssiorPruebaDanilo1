package com.example.exellsior.services;

import com.example.exellsior.entity.Client;
import com.example.exellsior.entity.ClientVehicle;
import com.example.exellsior.entity.Space;
import com.example.exellsior.entity.VehicleType;
import com.example.exellsior.repository.ClientRepository;
import com.example.exellsior.repository.SpaceRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Service
public class ClientService {
    @Autowired
    private ClientRepository clientRepository;

    @Autowired
    private SpaceRepository spaceRepository;


    @Autowired
    private VehicleTypeService vehicleTypeService;

    public List<Client> getAllClients() {
        return clientRepository.findAll();
    }

    public Client getById(Long id) {
        return clientRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Cliente no encontrado"));
    }

    private List<VehicleType> resolveVehicleTypes(List<VehicleType> input) {
        if (input == null) return new ArrayList<>();
        List<VehicleType> resolved = new ArrayList<>();
        for (VehicleType vt : input) {
            if (vt != null && vt.getId() != null) {
                resolved.add(vehicleTypeService.getById(vt.getId()));
            }
        }
        return resolved;
    }

    private List<ClientVehicle> resolveClientVehicles(Client client, List<ClientVehicle> input) {
        if (input == null) return new ArrayList<>();

        List<ClientVehicle> resolved = new ArrayList<>();

        for (ClientVehicle cv : input) {
            if (cv == null || cv.getVehicleType() == null || cv.getVehicleType().getId() == null) continue;

            VehicleType vt = vehicleTypeService.getById(cv.getVehicleType().getId());

            ClientVehicle entity = new ClientVehicle();
            entity.setClient(client);
            entity.setVehicleType(vt);
            entity.setPlate(cv.getPlate());
            entity.setNotes(cv.getNotes());

            resolved.add(entity);
        }

        if (resolved.size() > 4) {
            throw new RuntimeException("Solo se permiten hasta 4 vehículos por cliente");
        }

        return resolved;
    }


    private void replaceClientVehicles(Client client, List<ClientVehicle> resolved) {
        List<ClientVehicle> current = client.getClientVehicles();
        if (current == null) {
            current = new ArrayList<>();
            client.setClientVehicles(current);
        } else {
            current.clear();
        }
        current.addAll(resolved);
    }




    private void ensureMax4(List<VehicleType> list) {
        if (list.size() > 4) {
            throw new RuntimeException("Solo se permiten hasta 4 vehículos por cliente");
        }
    }



    @Transactional
    public Client saveClient(Client client) {
        List<ClientVehicle> resolved = resolveClientVehicles(client, client.getClientVehicles());
        replaceClientVehicles(client, resolved);
        return clientRepository.save(client);
    }



    @Transactional
    public Client updateClientPartially(Long id, Map<String, Object> updates) {
        Client client = getById(id);

        if (updates.containsKey("price")) client.setPrice((Integer) updates.get("price"));
        if (updates.containsKey("code")) client.setCode((String) updates.get("code"));
        if (updates.containsKey("paymentMethod")) client.setPaymentMethod((String) updates.get("paymentMethod"));

        if (updates.containsKey("clover")) {
            Object cloverObj = updates.get("clover");
            client.setClover(cloverObj != null ? Integer.valueOf(cloverObj.toString()) : null);
        }

        if (updates.containsKey("clientVehicles")) {
            Object vts = updates.get("clientVehicles");
            if (vts instanceof List) {
                List<?> list = (List<?>) vts;
                List<ClientVehicle> incoming = new ArrayList<>();

                for (Object item : list) {
                    if (item instanceof Map) {
                        Map<?, ?> m = (Map<?, ?>) item;
                        Object vtObj = m.get("vehicleType");

                        if (vtObj instanceof Map) {
                            Object idObj = ((Map<?, ?>) vtObj).get("id");
                            if (idObj != null) {
                                ClientVehicle cv = new ClientVehicle();
                                VehicleType vt = vehicleTypeService.getById(Long.valueOf(idObj.toString()));
                                cv.setVehicleType(vt);
                                cv.setPlate(m.get("plate") != null ? m.get("plate").toString() : null);
                                cv.setNotes(m.get("notes") != null ? m.get("notes").toString() : null);
                                incoming.add(cv);
                            }
                        }
                    }
                }

                List<ClientVehicle> resolved = resolveClientVehicles(client, incoming);
                replaceClientVehicles(client, resolved);
            }
        }

        return clientRepository.save(client);
    }


    @Transactional
    public void deleteClient(Long id) {
        Client client = getById(id);

        // Buscar si tiene espacio asociado
        String spaceKey = client.getSpaceKey();
        if (spaceKey != null) {
            Space space = spaceRepository.findById(spaceKey).orElse(null);
            if (space != null) {
                space.setOccupied(false);
                space.setHold(false);
                space.setClientId(null);
                space.setStartTime(null);
                space.setClient(null);
                spaceRepository.save(space);
                System.out.println("Espacio " + spaceKey + " liberado al eliminar cliente " + id);
            }
        }

        // Eliminar cliente
        clientRepository.delete(client);
    }




    // ClientService.java
    public Client getByDni(String dni) {
        return clientRepository.findFirstByDniOrderByIdDesc(dni).orElse(null);
    }


    public List<Client> getReservationsByDni(String dni) {
        if (dni == null || dni.trim().isEmpty()) return List.of();
        return clientRepository.findByDniOrderByIdDesc(dni.trim());
    }




    @Transactional
    public Client reserveSpace(String spaceKey, Client clientData) {
        Space targetSpace = spaceRepository.findById(spaceKey)
                .orElseThrow(() -> new RuntimeException("Espacio no encontrado: " + spaceKey));

        if (targetSpace.isOccupied()) {
            throw new RuntimeException("El espacio ya está ocupado");
        }

        Client client;
        if (clientData.getId() != null) {
            client = clientRepository.findById(clientData.getId())
                    .orElseThrow(() -> new RuntimeException("Cliente no encontrado"));

            String oldSpaceKey = client.getSpaceKey();
            if (oldSpaceKey != null && !oldSpaceKey.equals(spaceKey)) {
                Space oldSpace = spaceRepository.findById(oldSpaceKey).orElse(null);
                if (oldSpace != null) {
                    oldSpace.setOccupied(false);
                    oldSpace.setHold(false);
                    oldSpace.setClientId(null);
                    oldSpace.setStartTime(null);
                    spaceRepository.save(oldSpace);
                }
            }
        } else {
            client = new Client();
        }

        client.setName(clientData.getName());
        client.setDni(clientData.getDni());
        client.setPhoneRaw(clientData.getPhoneRaw());
        client.setPhoneIntl(clientData.getPhoneIntl());
        client.setCode(clientData.getCode());
        client.setVehicle(clientData.getVehicle());
        client.setPlate(clientData.getPlate());
        client.setNotes(clientData.getNotes());
        client.setCategory(clientData.getCategory());
        client.setPrice(clientData.getPrice());
        client.setSpaceKey(spaceKey);
        client.setClover(clientData.getClover());
        client.setEntryTimestamp(new Date());
        client.setExitTimestamp(null);

        // ✅ reemplazo seguro
        List<ClientVehicle> resolved = resolveClientVehicles(client, clientData.getClientVehicles());
        replaceClientVehicles(client, resolved);

        client = clientRepository.save(client);

        targetSpace.setOccupied(true);
        targetSpace.setHold(false);
        targetSpace.setClientId(client.getId());
        targetSpace.setStartTime(System.currentTimeMillis());
        spaceRepository.save(targetSpace);

        return client;
    }


    @Transactional
    public void releaseSpace(String spaceKey) {
        Space space = spaceRepository.findById(spaceKey)
                .orElseThrow(() -> new RuntimeException("Espacio no encontrado: " + spaceKey));

        Long clientId = space.getClientId();

        // Liberar espacio
        space.setOccupied(false);
        space.setHold(false);
        space.setClientId(null);
        space.setStartTime(null);
        spaceRepository.save(space);

        if (clientId != null) {
            Client client = clientRepository.findById(clientId)
                    .orElseThrow(() -> new RuntimeException("Cliente no encontrado"));

           // client.setSpaceKey(null);
            client.setExitTimestamp(System.currentTimeMillis());  // ← REGISTRAR FECHA DE SALIDA

            // NO limpiar otros datos (price, vehicle, etc.)
            clientRepository.save(client);
        }

        System.out.println("Espacio liberado. Cliente " + clientId + " marcado con exitTimestamp");
    }



    @Transactional
    public void resetAllData() {
        System.out.println("=== INICIO DEL CIERRE DEL DÍA ===");
        System.out.println("Fecha/Hora: " + new Date());

        // 1. Cargar todos los espacios y clientes
        System.out.println("Cargando espacios y clientes...");
        List<Space> allSpaces = spaceRepository.findAll();
        List<Client> allClients = clientRepository.findAll();

        List<Client> clientsToUpdate = new ArrayList<>();

        // 2. Liberar espacios (siempre)
        System.out.println("Liberando espacios...");
        for (Space space : allSpaces) {
            if (space.isOccupied()) {
                System.out.println(" → Liberando espacio ocupado: " + space.getKey());
                space.setOccupied(false);
                space.setHold(false);
                space.setClientId(null);
                space.setStartTime(null);
            } else {
                System.out.println(" → Espacio ya libre: " + space.getKey());
            }
        }
        spaceRepository.saveAll(allSpaces);

        // 3. Limpiar clientes que aún tienen spaceKey no null (inconsistencias)
        System.out.println("Buscando clientes con spaceKey no reseteado...");
        for (Client client : allClients) {
            if (client.getSpaceKey() != null) {
                System.out.println(" → Cliente con spaceKey pendiente: ID " + client.getId() +
                        " | spaceKey: " + client.getSpaceKey() +
                        " | entryTimestamp: " + client.getEntryTimestamp() +
                        " | exitTimestamp: " + client.getExitTimestamp());

                client.setSpaceKey(null);
                client.setEntryTimestamp(null);

                // Solo poner exitTimestamp si NO tenía salida previa
                if (client.getExitTimestamp() == null) {
                    long now = System.currentTimeMillis();
                    client.setExitTimestamp(now);
                    System.out.println("   → Salida forzada aplicada: " + new Date(now));
                } else {
                    System.out.println("   → Salida previa detectada (" + new Date(client.getExitTimestamp()) + "). No se actualiza.");
                }

                clientsToUpdate.add(client);
            }
        }

        // 4. Guardar clientes actualizados
        if (!clientsToUpdate.isEmpty()) {
            System.out.println("Guardando " + clientsToUpdate.size() + " clientes con campos reseteados...");
            clientRepository.saveAll(clientsToUpdate);
            System.out.println("Clientes guardados.");
        } else {
            System.out.println("No hay clientes con spaceKey pendiente.");
        }

        System.out.println("=== CIERRE DEL DÍA FINALIZADO ===");
        System.out.println("Espacios procesados: " + allSpaces.size());
        System.out.println("Clientes reseteados: " + clientsToUpdate.size());
        System.out.println("=================================");
    }


    public List<Client> getUniqueClients() {
        List<Client> all = clientRepository.findAll();

        // Ordenar descendente por "recencia"
        all.sort((a, b) -> {
            long aTs = getClientSortTs(a);
            long bTs = getClientSortTs(b);
            return Long.compare(bTs, aTs);
        });

        Map<String, Client> unique = new LinkedHashMap<>();

        for (Client c : all) {
            String key = buildClientIdentityKey(c);
            if (!unique.containsKey(key)) {
                unique.put(key, c); // como ya está ordenado desc, el primero es el más reciente
            }
        }

        return new ArrayList<>(unique.values());
    }

    private long getClientSortTs(Client c) {
        // prioridad: entryTimestamp, luego exitTimestamp, luego id
        if (c.getEntryTimestamp() != null) return c.getEntryTimestamp().getTime();
        if (c.getExitTimestamp() != null) return c.getExitTimestamp();
        return c.getId() != null ? c.getId() : 0L;
    }

    private String buildClientIdentityKey(Client c) {
        String dni = c.getDni() != null ? c.getDni().trim() : "";
        String phone = c.getPhoneIntl() != null ? c.getPhoneIntl().replaceAll("\\D", "") : "";
        String name = c.getName() != null ? c.getName().trim().toLowerCase() : "";

        if (!dni.isBlank()) return "dni:" + dni;
        if (!phone.isBlank()) return "phone:" + phone;
        return "name:" + name;
    }

}
