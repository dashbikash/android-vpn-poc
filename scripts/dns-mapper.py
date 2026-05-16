import psycopg2
from psycopg2 import Error
from dns import resolver, reversename

def read_postgres_data():
    connection = None
    try:
        # 1. Connect to your database
        connection = psycopg2.connect(
            user="postgres",
            password="password",
            host="localhost",
            port="5432",
            database="context-db"
        )

        # 2. Create a cursor to perform database operations
        cursor = connection.cursor()

        # 3. Execute a SELECT query
        select_query = "SELECT DISTINCT IP FROM TRAFFIC_LOGS LIMIT 100"
        insert_query = "INSERT INTO IP_DNS_MAP (IP, DOMAIN) VALUES (%s, %s)"
        cursor.execute(select_query)

        # 4. Fetch the results
        records = cursor.fetchall()

        print(f"Total rows retrieved: {len(records)}\n")

        # 5. Iterate through the data
        for row in records:
            try:
                addr = reversename.from_address(row[0])
                answers = resolver.resolve(addr, "PTR") 
                print("IP: {} ({}):".format(row[0],len(answers))) 
                for rdata in answers:
                    print("   Domain: ", rdata.to_text())

            except Exception as e:
                pass
                #print(f"{row[0]} -> Domain not found : {e}")

    except (Exception, Error) as error:
        print(f"Error while connecting to PostgreSQL: {error}")

    finally:
        # 6. Closing database connection
        if connection:
            cursor.close()
            connection.close()
            print("\nPostgreSQL connection is closed.")

if __name__ == "__main__":
    read_postgres_data()