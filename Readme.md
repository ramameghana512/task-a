# Receipt Processing Service

## Requirements

- Upload a receipt file and store it
- Process the receipt: run OCR on it, parse the text, and create a transaction with its taxes and line items
- Fetch a transaction along with its taxes and line items
- Re-itemize a transaction: re-run item extraction on the stored OCR text without creating a new transaction
- Edit line items in place (description, amount, quantity)
- Merge two or more line items into one
- Split one line item into multiple line items
- Always keep line items + taxes reconciled against the transaction total; reject any edit that breaks this
- Prevent concurrent itemize/patch operations on the same transaction using status-based claim locks

## How to Run

Prerequisites: Java 17, Maven.

1. Update the SQLite database path in `src/main/resources/application.yaml` under `spring.datasource.url` to a path on your machine.
2. Start the app:
   ```
   mvn spring-boot:run
   ```
3. The service starts on port 8080. Confirm it's up:
   ```
   curl http://localhost:8080/health
   ```

## Entities

### Receipt
- id
- originalFilename
- filePath
- fileSize
- contentType
- status (UPLOADED, FAILED)
- createdAt
- updatedAt

### Transaction
Columns on the transactions table:
- id
- receiptId (one receipt maps to exactly one transaction)
- merchant
- transactionDate
- currency
- total
- processingStatus (PROCESSING, PROCESSED, FAILED)
- itemizeStatus (COMPLETE, NEEDS_REVIEW, FAILED, ITEMIZING, PATCHING)
- rawOcrText (kept so the transaction can be re-itemized later)
- createdAt
- updatedAt

Not columns on this table, but child rows in their own tables (taxes, line_items) linked back via a transaction_id foreign key, and loaded together when the transaction is fetched:
- taxes (one-to-many)
- lineItems (one-to-many)

### LineItem
- id
- transaction (many-to-one)
- description
- amount
- quantity
- createdAt
- updatedAt

### Tax
- id
- transaction (many-to-one)
- name (e.g. VAT, GST)
- rate
- amount
- jurisdiction
- createdAt

## Repositories

### ReceiptRepository
- save(receipt)
- findById(id)

### TransactionRepository
- save(transaction)
- findById(id)
- findByIdWithChildren(id)
- findByReceiptId(receiptId)
- findByReceiptIdWithChildren(receiptId)
- claimForRetry(receiptId, rawOcrText)
- markProcessingFailed(id)
- claimForItemizing(id)
- markItemizeFailed(id)
- claimForPatching(id)
- markPatchFailed(id)
- setItemizeStatus(id, status)

### LineItemRepository
- saveAll(items)
- findByTransactionId(transactionId)
- deleteByTransactionId(transactionId)
- updateAll(items)

### TaxRepository
- saveAll(taxes)
- findByTransactionId(transactionId)
- deleteByTransactionId(transactionId)

## Service Layer

### ReceiptStorageService
- upload(file): stores the uploaded file and creates a Receipt record

### ReceiptProcessingService
- processReceipt(receiptId): runs OCR on the stored file, parses the text, and creates the Transaction with its taxes and line items

### ReceiptProcessingUnit
- saveAndComplete(transactionId, parsedDocument): persists the parsed transaction, taxes and line items in a single unit of work

### TransactionService
- getTransaction(transactionId): fetches a transaction with its taxes and line items
- reItemize(transactionId): re-runs item extraction on the stored OCR text and replaces the line items
- editItems(transactionId, edits): updates description/amount/quantity on existing line items in place
- mergeItems(transactionId, mergeIds, description, amount): removes the given line items and replaces them with one merged item
- splitItem(transactionId, splitId, splitItems): removes one line item and replaces it with multiple new items

### TransactionItemizationUnit
- replaceAndComplete(transactionId, items): replaces all line items for a transaction as one unit of work

### TransactionPatchUnit
- apply(transactionId, finalItems): applies an edit/merge/split as one unit of work, choosing an in-place update when the item count is unchanged or a delete-and-insert when it changes

All edit/merge/split operations validate that the final line items plus taxes still add up to the transaction total before saving, and reject the change with a 409 if they don't.

## Example Sample Curl Requests

### Upload a receipt
```
curl --request post \
  --url http://localhost:8080/receipts \
  --header 'Content-Type: multipart/form-data' \
  --form file=@/Users/daliparthi.meghana/Downloads/task-a/fixtures/task-a/receipt-tax-only.txt
```
```
{
  "receipt_id": "74816d47-4e1e-4ac4-9516-077f5afb7140"
}
```

### Process a receipt
```
curl --request post \
  --url http://localhost:8080/receipts/74816d47-4e1e-4ac4-9516-077f5afb7140/process
```
```
{
  "id": "788ab173-f7e7-4b92-97e2-570dcfd752dc",
  "receiptId": "74816d47-4e1e-4ac4-9516-077f5afb7140",
  "merchant": "Berlin Taxi GmbH",
  "transactionDate": "2026-03-12",
  "currency": "EUR",
  "total": 24.00,
  "processingStatus": "PROCESSED",
  "taxes": [
    {
      "id": "43115652-9717-46f3-a607-d6ef8b33a483",
      "name": "VAT",
      "rate": 0.19,
      "amount": 3.83,
      "jurisdiction": null,
      "createdAt": "2026-09-28T09:19:16.917004"
    }
  ],
  "lineItems": [
    {
      "id": "c309b010-da60-40c0-84ac-053b90eb5f92",
      "description": "Trip fare",
      "amount": 24.00,
      "quantity": 1,
      "createdAt": "2026-09-28T09:19:16.917095",
      "updatedAt": "2026-09-28T09:19:16.917096"
    }
  ],
  "createdAt": "2026-09-28T09:19:16.911",
  "updatedAt": "2026-09-28T09:19:16.922064",
  "itemize_status": "NEEDS_REVIEW"
}
```

### Get a transaction
```
curl --request get \
  --url http://localhost:8080/transactions/788ab173-f7e7-4b92-97e2-570dcfd752dc
```
```
{
  "id": "788ab173-f7e7-4b92-97e2-570dcfd752dc",
  "receiptId": "74816d47-4e1e-4ac4-9516-077f5afb7140",
  "merchant": "Berlin Taxi GmbH",
  "transactionDate": "2026-03-12",
  "currency": "EUR",
  "total": 24,
  "processingStatus": "PROCESSED",
  "taxes": [
    {
      "id": "43115652-9717-46f3-a607-d6ef8b33a483",
      "name": "VAT",
      "rate": 0.19,
      "amount": 3.83,
      "jurisdiction": null,
      "createdAt": "2026-09-28T09:19:16.917"
    }
  ],
  "lineItems": [
    {
      "id": "6d266536-bf88-4afa-9be3-65466a314b2f",
      "description": "Trip fare",
      "amount": 24,
      "quantity": 1,
      "createdAt": "2026-09-28T09:19:46.855",
      "updatedAt": "2026-09-28T09:19:46.855"
    }
  ],
  "createdAt": "2026-09-28T09:19:16.911",
  "updatedAt": "2026-09-28T09:19:16.922",
  "itemize_status": "NEEDS_REVIEW"
}
```

### Re-itemize a transaction
```
curl --request post \
  --url http://localhost:8080/transactions/788ab173-f7e7-4b92-97e2-570dcfd752dc/itemize
```
```
{
  "id": "788ab173-f7e7-4b92-97e2-570dcfd752dc",
  "receiptId": "74816d47-4e1e-4ac4-9516-077f5afb7140",
  "merchant": "Berlin Taxi GmbH",
  "transactionDate": "2026-03-12",
  "currency": "EUR",
  "total": 24,
  "processingStatus": "PROCESSED",
  "taxes": [
    {
      "id": "43115652-9717-46f3-a607-d6ef8b33a483",
      "name": "VAT",
      "rate": 0.19,
      "amount": 3.83,
      "jurisdiction": null,
      "createdAt": "2026-09-28T09:19:16.917"
    }
  ],
  "lineItems": [
    {
      "id": "6d266536-bf88-4afa-9be3-65466a314b2f",
      "description": "Trip fare",
      "amount": 24,
      "quantity": 1,
      "createdAt": "2026-09-28T09:19:46.855",
      "updatedAt": "2026-09-28T09:19:46.855"
    }
  ],
  "createdAt": "2026-09-28T09:19:16.911",
  "updatedAt": "2026-09-28T09:19:16.922",
  "itemize_status": "NEEDS_REVIEW"
}
```

### Edit an item (fails reconciliation example)
```
curl --request patch \
  --url http://localhost:8080/transactions/788ab173-f7e7-4b92-97e2-570dcfd752dc/items/edit \
  --header 'Content-Type: application/json' \
  --data '{
  "items": [
    {
      "id": "4674425c-86d0-43eb-b850-454e80ae823f",
      "description": "Snacks",
      "amount": 12.6,
      "quantity": 1
    }
  ]
}'
```
```
{
  "status": 409,
  "title": "Transaction Reconciliation Failed",
  "detail": "Transaction 788ab173-f7e7-4b92-97e2-570dcfd752dc does not reconcile. Expected 24 but got 24.83."
}
```

### Merge items (for another transaction)
```
curl --request patch \
  --url http://localhost:8080/transactions/e4642b7e-fb8f-4cc3-90e9-efe663c11cb7/items/merge \
  --header 'Content-Type: application/json' \
  --data '{
  "merge_ids": [
    "27cd47f3-545b-48ce-85a6-15be56eb0039",
    "4674425c-86d0-43eb-b850-454e80ae823f"
  ],
  "description": "Lunch combo",
  "amount": 16.6
}'
```
```
{
  "id": "e4642b7e-fb8f-4cc3-90e9-efe663c11cb7",
  "receiptId": "cb21e062-5a53-4318-994d-d520cd0e5ef3",
  "merchant": "Hotel Shop",
  "transactionDate": "2026-03-13",
  "currency": "EUR",
  "total": 18.5,
  "processingStatus": "PROCESSED",
  "taxes": [
    {
      "id": "57c24514-10a6-4063-86fa-c44fe41be9bd",
      "name": "VAT",
      "rate": 0.19,
      "amount": 1.9,
      "jurisdiction": null,
      "createdAt": "2026-09-28T09:11:33.348"
    }
  ],
  "lineItems": [
    {
      "id": "7d60ee1a-e0df-454e-a8ec-b828dc9fa832",
      "description": "Lunch combo",
      "amount": 16.6,
      "quantity": 1,
      "createdAt": "2026-09-28T09:18:47.054",
      "updatedAt": "2026-09-28T09:18:47.054"
    }
  ],
  "createdAt": "2026-09-28T09:11:33.345",
  "updatedAt": "2026-09-28T09:11:33.351",
  "itemize_status": "COMPLETE"
}
```

### Split an item
```
curl --request patch \
  --url http://localhost:8080/transactions/ca623092-9b8b-44c0-bfc7-f9074fb6fe47/items/split \
  --header 'Content-Type: application/json' \
  --data '{
  "split_id": "e2338068-6482-410f-940a-cc9187decab9",
  "items": [
    {
      "description": "Sandwich",
      "amount": 8.9,
      "quantity": 1
    },
    {
      "description": "Coke",
      "amount": 3.5,
      "quantity": 1
    }
  ]
}'
```
```
{
  "id": "ca623092-9b8b-44c0-bfc7-f9074fb6fe47",
  "receiptId": "0aeaf345-1e6a-4f54-b91d-1837f0f2ce83",
  "merchant": "Cafe Mitte",
  "transactionDate": "2026-03-12",
  "currency": "EUR",
  "total": 17.85,
  "processingStatus": "PROCESSED",
  "taxes": [
    {
      "id": "655429af-0072-42cb-ac51-9ba54e153d74",
      "name": "VAT",
      "rate": 0.19,
      "amount": 2.85,
      "jurisdiction": null,
      "createdAt": "2026-09-28T09:05:35.328"
    }
  ],
  "lineItems": [
    {
      "id": "ef47b9f4-c4a6-4694-9411-ba82fab4919e",
      "description": "Mineral water",
      "amount": 2.6,
      "quantity": 1,
      "createdAt": "2026-09-28T09:05:35.328",
      "updatedAt": "2026-09-28T09:05:35.328"
    },
    {
      "id": "a1b2c3d4-e5f6-4789-9abc-def012345678",
      "description": "Sandwich",
      "amount": 8.9,
      "quantity": 1,
      "createdAt": "2026-09-28T09:25:10.512",
      "updatedAt": "2026-09-28T09:25:10.512"
    },
    {
      "id": "b2c3d4e5-f6a7-4890-abcd-ef0123456789",
      "description": "Coke",
      "amount": 3.5,
      "quantity": 1,
      "createdAt": "2026-09-28T09:25:10.512",
      "updatedAt": "2026-09-28T09:25:10.512"
    }
  ],
  "createdAt": "2026-09-28T09:05:35.317",
  "updatedAt": "2026-09-28T09:25:10.512",
  "itemize_status": "COMPLETE"
}
```

